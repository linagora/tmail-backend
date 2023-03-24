FROM golang:1.15.8 AS build

WORKDIR /app
COPY . .

RUN GOPROXY=https://goproxy.io go get -d -v ./... && \
    go build -o jwt-revoker

FROM gcr.io/distroless/base-debian11
COPY --from=build /app/jwt-revoker .
EXPOSE 8080
CMD ["/jwt-revoker"]
