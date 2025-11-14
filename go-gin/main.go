package main

import (
	"net/http"
	"sync/atomic"

	"github.com/gin-gonic/gin"
)

type VoteRequest struct {
	UserID      string `json:"userId" binding:"required"`
	CandidateID string `json:"candidateId" binding:"required"`
}

type VoteResponse struct {
	Success    bool   `json:"success"`
	Message    string `json:"message"`
	TotalVotes int64  `json:"totalVotes"`
}

type HealthResponse struct {
	Status  string `json:"status"`
	Service string `json:"service"`
}

var voteCounter atomic.Int64

func main() {
	// Set Gin to release mode for production performance
	gin.SetMode(gin.ReleaseMode)

	router := gin.New()
	router.Use(gin.Recovery())

	router.POST("/api/vote", func(c *gin.Context) {
		var req VoteRequest
		if err := c.ShouldBindJSON(&req); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		totalVotes := voteCounter.Add(1)

		c.JSON(http.StatusOK, VoteResponse{
			Success:    true,
			Message:    "Vote recorded successfully",
			TotalVotes: totalVotes,
		})
	})

	router.GET("/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, HealthResponse{
			Status:  "UP",
			Service: "go-gin-voting-poc",
		})
	})

	router.Run(":8080")
}
