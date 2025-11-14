package com.voting.model

data class VoteResponse(
    val success: Boolean,
    val message: String,
    val totalVotes: Long
)
