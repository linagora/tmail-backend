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

package com.linagora.tmail.james.jmap.label;

import static com.linagora.tmail.james.jmap.label.PostgresLabelModule.LabelTable.COLOR;
import static com.linagora.tmail.james.jmap.label.PostgresLabelModule.LabelTable.DISPLAY_NAME;
import static com.linagora.tmail.james.jmap.label.PostgresLabelModule.LabelTable.DOCUMENTATION;
import static com.linagora.tmail.james.jmap.label.PostgresLabelModule.LabelTable.KEYWORD;
import static com.linagora.tmail.james.jmap.label.PostgresLabelModule.LabelTable.TABLE_NAME;
import static com.linagora.tmail.james.jmap.label.PostgresLabelModule.LabelTable.USERNAME;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.jooq.Record;
import org.jooq.UpdateSetFirstStep;
import org.jooq.UpdateSetMoreStep;

import com.linagora.tmail.james.jmap.model.Color;
import com.linagora.tmail.james.jmap.model.DisplayName;
import com.linagora.tmail.james.jmap.model.Label;
import com.linagora.tmail.james.jmap.model.LabelId;
import com.linagora.tmail.james.jmap.model.LabelNotFoundException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.Option;
import scala.jdk.javaapi.OptionConverters;

public class PostgresLabelDAO {
    private final PostgresExecutor postgresExecutor;

    public PostgresLabelDAO(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Mono<Label> insert(Username username, Label label) {
        return postgresExecutor.executeVoid(dsl -> Mono.from(dsl.insertInto(TABLE_NAME)
            .set(USERNAME, username.asString())
            .set(KEYWORD, label.keyword())
            .set(DISPLAY_NAME, label.displayName().value())
            .set(COLOR, OptionConverters.toJava(label.color())
                .map(Color::value)
                .orElse(null))
            .set(DOCUMENTATION,OptionConverters.toJava(label.documentation())
                .orElse(null))))
            .thenReturn(label);
    }

    public Flux<Label> selectAll(Username username) {
        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.select(KEYWORD, DISPLAY_NAME, COLOR)
                .from(TABLE_NAME)
                .where(USERNAME.eq(username.asString()))))
            .map(PostgresLabelDAOUtils::toLabel);
    }

    public Flux<Label> selectSome(Username username, Collection<String> keywords) {
        if (keywords.isEmpty()) {
            return Flux.empty();
        }

        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.select(KEYWORD, DISPLAY_NAME, COLOR, DOCUMENTATION)
                .from(TABLE_NAME)
                .where(USERNAME.eq(username.asString()),
                    KEYWORD.in(keywords))))
            .map(PostgresLabelDAOUtils::toLabel);
    }

    public Mono<Void> updateLabel(Username username, LabelId labelId, Option<DisplayName> newDisplayName, Option<Color> newColor, Option<String> newDocumentation) {
        return postgresExecutor.executeReturnAffectedRowsCount(dsl -> {
                UpdateSetFirstStep<Record> originalUpdateStatement = dsl.update(TABLE_NAME);
                Optional<UpdateSetMoreStep<Record>> updateOnlyDisplayNameStatement = addUpdateDisplayName(newDisplayName, originalUpdateStatement);
                Optional<UpdateSetMoreStep<Record>> updateColorStatement = addUpdateColor(newColor, originalUpdateStatement, updateOnlyDisplayNameStatement);

                return addUpdateDocumentation(newDocumentation, originalUpdateStatement, updateOnlyDisplayNameStatement,updateColorStatement)
                    .map(executeLabelUpdateStatementMono(username, labelId.toKeyword()))
                    .orElseGet(() -> updateOnlyDisplayNameStatement.map(executeLabelUpdateStatementMono(username, labelId.toKeyword()))
                        .orElseGet(Mono::empty));
            })
            .handle((updatedLabelCount, sink) -> {
                if (updatedLabelCount == 0) {
                    sink.error(new LabelNotFoundException(labelId));
                }
            });
    }

    private Function<UpdateSetMoreStep<Record>, Mono<Integer>> executeLabelUpdateStatementMono(Username username, String keyword) {
        return updateLabelStatement -> Mono.from(updateLabelStatement.where(USERNAME.eq(username.asString()), KEYWORD.eq(keyword)));
    }

    private Optional<UpdateSetMoreStep<Record>> addUpdateDisplayName(Option<DisplayName> newDisplayName, UpdateSetFirstStep<Record> originalUpdateStatement) {
        return OptionConverters.toJava(newDisplayName)
            .map(displayName -> originalUpdateStatement.set(DISPLAY_NAME, displayName.value()));
    }

    private Optional<UpdateSetMoreStep<Record>> addUpdateColor(Option<Color> newColor, UpdateSetFirstStep<Record> originalUpdateStatement, Optional<UpdateSetMoreStep<Record>> updateDisplayNameStatement) {
        return OptionConverters.toJava(newColor)
            .map(color -> updateDisplayNameStatement.map(statement -> statement.set(COLOR, color.value()))
                .orElseGet(() -> originalUpdateStatement.set(COLOR, color.value())));
    }

    private Optional<UpdateSetMoreStep<Record>> addUpdateDocumentation(Option<String> newDocumentation, UpdateSetFirstStep<Record> originalUpdateStatement, Optional<UpdateSetMoreStep<Record>> updateDisplayNameStatement, Optional<UpdateSetMoreStep<Record>> updateColorStatement) {
        return OptionConverters.toJava(newDocumentation)
            .map(documentation -> updateColorStatement.map(statement -> statement.set(DOCUMENTATION, documentation))
                .orElseGet(() -> updateDisplayNameStatement.map(statement -> statement.set(DOCUMENTATION, documentation))
                    .orElseGet(() -> originalUpdateStatement.set(DOCUMENTATION, documentation))));
    }

    public Mono<Void> deleteOne(Username username, String keyword) {
        return postgresExecutor.executeVoid(dsl -> Mono.from(dsl.deleteFrom(TABLE_NAME)
            .where(USERNAME.eq(username.asString()),
                KEYWORD.eq(keyword))));
    }

    public Mono<Void> deleteAll(Username username) {
        return postgresExecutor.executeVoid(dsl -> Mono.from(dsl.deleteFrom(TABLE_NAME)
            .where(USERNAME.eq(username.asString()))));
    }
}
