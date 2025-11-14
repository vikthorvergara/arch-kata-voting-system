package com.voting.model

data class VoteRequest(
    val userId: String,
    val candidateId: String
)
