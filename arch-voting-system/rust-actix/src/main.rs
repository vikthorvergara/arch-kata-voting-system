use actix_web::{web, App, HttpResponse, HttpServer, Responder};
use serde::{Deserialize, Serialize};
use std::sync::atomic::{AtomicI64, Ordering};

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

struct AppState {
    vote_counter: AtomicI64,
}

async fn vote(data: web::Data<AppState>, req: web::Json<VoteRequest>) -> impl Responder {
    if req.user_id.is_empty() || req.candidate_id.is_empty() {
        return HttpResponse::BadRequest().json(serde_json::json!({
            "error": "userId and candidateId are required"
        }));
    }

    let total_votes = data.vote_counter.fetch_add(1, Ordering::Relaxed) + 1;

    HttpResponse::Ok().json(VoteResponse {
        success: true,
        message: "Vote recorded successfully".to_string(),
        total_votes,
    })
}

async fn health() -> impl Responder {
    HttpResponse::Ok().json(HealthResponse {
        status: "UP".to_string(),
        service: "rust-actix-voting-poc".to_string(),
    })
}

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    let app_state = web::Data::new(AppState {
        vote_counter: AtomicI64::new(0),
    });

    println!("Starting Actix Web server on 0.0.0.0:8080");

    HttpServer::new(move || {
        App::new()
            .app_data(app_state.clone())
            .route("/api/vote", web::post().to(vote))
            .route("/health", web::get().to(health))
    })
    .bind("0.0.0.0:8080")?
    .workers(num_cpus::get())
    .run()
    .await
}
