use axum::{
    extract::State,
    http::StatusCode,
    response::IntoResponse,
    routing::{get, post},
    Json, Router,
};
use serde::{Deserialize, Serialize};
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::Arc;

#[derive(Deserialize)]
struct VoteRequest {
    #[serde(rename = "userId")]
    user_id: String,
    #[serde(rename = "candidateId")]
    candidate_id: String,
}

#[derive(Serialize)]
struct VoteResponse {
    success: bool,
    message: String,
    #[serde(rename = "totalVotes")]
    total_votes: i64,
}

#[derive(Serialize)]
struct HealthResponse {
    status: String,
    service: String,
}

#[derive(Serialize)]
struct ErrorResponse {
    error: String,
}

struct AppState {
    vote_counter: AtomicI64,
}

async fn vote(
    State(state): State<Arc<AppState>>,
    Json(req): Json<VoteRequest>,
) -> impl IntoResponse {
    if req.user_id.is_empty() || req.candidate_id.is_empty() {
        return (
            StatusCode::BAD_REQUEST,
            Json(ErrorResponse {
                error: "userId and candidateId are required".to_string(),
            }),
        )
            .into_response();
    }

    let total_votes = state.vote_counter.fetch_add(1, Ordering::Relaxed) + 1;

    (
        StatusCode::OK,
        Json(VoteResponse {
            success: true,
            message: "Vote recorded successfully".to_string(),
            total_votes,
        }),
    )
        .into_response()
}

async fn health() -> impl IntoResponse {
    Json(HealthResponse {
        status: "UP".to_string(),
        service: "rust-axum-voting-poc".to_string(),
    })
}

#[tokio::main]
async fn main() {
    let app_state = Arc::new(AppState {
        vote_counter: AtomicI64::new(0),
    });

    let app = Router::new()
        .route("/api/vote", post(vote))
        .route("/health", get(health))
        .with_state(app_state);

    let listener = tokio::net::TcpListener::bind("0.0.0.0:8080")
        .await
        .unwrap();

    println!("Starting Axum server on 0.0.0.0:8080");

    axum::serve(listener, app).await.unwrap();
}
