/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package org.apache.james.events;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.core.healthcheck.ResultStatus;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class AggregatedRabbitEventBusConsumerHealthCheck implements HealthCheck {
    private static final String CAUSE_SEPARATOR = "; ";

    private static ImmutableList<HealthCheck> buildDelegates(EventBus eventBus, List<NamingStrategy> namingStrategies,
                                                             SimpleConnectionPool connectionPool,
                                                             Group groupRegistrationHandlerGroup) {
        return namingStrategies.stream()
            .map(namingStrategy -> new RabbitEventBusConsumerHealthCheck(eventBus, namingStrategy, connectionPool, groupRegistrationHandlerGroup))
            .collect(ImmutableList.toImmutableList());
    }

    private static ComponentName asComponentName(List<NamingStrategy> namingStrategies) {
        Preconditions.checkArgument(!namingStrategies.isEmpty(), "At least one naming strategy is required");
        return new ComponentName(RabbitEventBusConsumerHealthCheck.COMPONENT + "-" + namingStrategies.getFirst().getEventBusName().value());
    }

    private final ComponentName componentName;
    private final ImmutableList<HealthCheck> delegates;

    public AggregatedRabbitEventBusConsumerHealthCheck(EventBus eventBus, List<NamingStrategy> namingStrategies,
                                                       SimpleConnectionPool connectionPool,
                                                       Group groupRegistrationHandlerGroup) {
        this(asComponentName(namingStrategies),
            buildDelegates(eventBus, namingStrategies, connectionPool, groupRegistrationHandlerGroup));
    }

    @VisibleForTesting
    AggregatedRabbitEventBusConsumerHealthCheck(ComponentName componentName, List<HealthCheck> delegates) {
        Preconditions.checkArgument(!delegates.isEmpty(), "At least one health check delegate is required");

        this.componentName = componentName;
        this.delegates = ImmutableList.copyOf(delegates);
    }

    @Override
    public ComponentName componentName() {
        return componentName;
    }

    @Override
    public Mono<Result> check() {
        return Flux.fromIterable(delegates)
            .concatMap(HealthCheck::check)
            .collectList()
            .map(this::mergeResults);
    }

    private Result mergeResults(List<Result> results) {
        ResultStatus mergedStatus = results.stream()
            .map(Result::getStatus)
            .reduce(ResultStatus.HEALTHY, ResultStatus::merge);

        return switch (mergedStatus) {
            case HEALTHY -> Result.healthy(componentName);
            case DEGRADED -> Result.degraded(componentName, mergedCause(results).orElse("At least one partition is degraded"));
            case UNHEALTHY -> firstError(results)
                .map(throwable -> Result.unhealthy(componentName, mergedCause(results).orElse("At least one partition is unhealthy"), throwable))
                .orElseGet(() -> Result.unhealthy(componentName, mergedCause(results).orElse("At least one partition is unhealthy")));
        };
    }

    private Optional<String> mergedCause(List<Result> results) {
        String cause = results.stream()
            .map(Result::getCause)
            .flatMap(Optional::stream)
            .collect(Collectors.joining(CAUSE_SEPARATOR));

        return cause.isEmpty() ? Optional.empty() : Optional.of(cause);
    }

    private Optional<Throwable> firstError(List<Result> results) {
        return results.stream()
            .map(Result::getError)
            .flatMap(Optional::stream)
            .findFirst();
    }
}
