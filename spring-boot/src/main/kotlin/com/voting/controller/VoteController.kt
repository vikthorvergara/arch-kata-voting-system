package com.voting.controller

import com.voting.model.HealthResponse
import com.voting.model.VoteRequest
import com.voting.model.VoteResponse
import org.springframework.web.bind.annotation.*
import java.util.concurrent.atomic.AtomicLong

@RestController
class VoteController {

    private val voteCounter = AtomicLong(0)

    @PostMapping("/api/vote")
    fun vote(@RequestBody request: VoteRequest): VoteResponse {
        val totalVotes = voteCounter.incrementAndGet()
        return VoteResponse(
            success = true,
            message = "Vote recorded successfully",
            totalVotes = totalVotes
        )
    }

    @GetMapping("/health")
    fun health(): HealthResponse {
        return HealthResponse(
            status = "UP",
            service = "spring-boot-voting-poc"
        )
    }
}
