package main

import (
	"log"
	"sync/atomic"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/recover"
)

type VoteRequest struct {
	UserID      string `json:"userId"`
	CandidateID string `json:"candidateId"`
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
	app := fiber.New(fiber.Config{
		Prefork:       false,
		StrictRouting: false,
		CaseSensitive: false,
		ServerHeader:  "Fiber",
		AppName:       "Voting System POC",
	})

	app.Use(recover.New())

	app.Post("/api/vote", func(c *fiber.Ctx) error {
		var req VoteRequest
		if err := c.BodyParser(&req); err != nil {
			return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
				"error": err.Error(),
			})
		}

		if req.UserID == "" || req.CandidateID == "" {
			return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
				"error": "userId and candidateId are required",
			})
		}

		totalVotes := voteCounter.Add(1)

		return c.JSON(VoteResponse{
			Success:    true,
			Message:    "Vote recorded successfully",
			TotalVotes: totalVotes,
		})
	})

	app.Get("/health", func(c *fiber.Ctx) error {
		return c.JSON(HealthResponse{
			Status:  "UP",
			Service: "go-fiber-voting-poc",
		})
	})

	log.Fatal(app.Listen(":8080"))
}
