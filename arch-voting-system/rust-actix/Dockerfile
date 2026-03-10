# Build stage
FROM rust:1.83-slim AS build
WORKDIR /app
COPY Cargo.toml ./
COPY src ./src
RUN cargo build --release

# Runtime stage
FROM debian:bookworm-slim
WORKDIR /app
COPY --from=build /app/target/release/voting-actix .

EXPOSE 8080

CMD ["./voting-actix"]
