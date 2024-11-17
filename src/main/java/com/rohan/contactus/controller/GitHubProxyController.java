package com.rohan.contactus.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;


@RestController
@RequestMapping("/github")
public class GitHubProxyController {

    private final RestTemplate restTemplate;
    private final String githubApiUrl = "https://api.github.com";

    @Value("${github.token}")
    private String githubToken;

    public GitHubProxyController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/repositories")
    public ResponseEntity<Object> getRepositories() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + githubToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Object> response = restTemplate.exchange(
                    githubApiUrl + "/user/repos",
                    HttpMethod.GET,
                    entity,
                    Object.class
            );
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching repositories");
        }
    }
    // Endpoint to get languages for a specific repository
    @GetMapping("/languages/{repoName}")
    public ResponseEntity<Object> getRepoLanguages(@PathVariable String repoName) {

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + githubToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            // Construct the GitHub API URL for fetching repository languages
            String url = String.format("https://api.github.com/repos/rohan3004/%s/languages", repoName);

            // Make the GET request to the GitHub API
            ResponseEntity<Object> response = restTemplate.exchange(
                    url, // URL for the repository languages
                    HttpMethod.GET,  // HTTP method
                    entity,  // Include the Authorization header
                    Object.class  // The response type (we expect a Map of languages)
            );

            // Return the response body (languages of the repository)
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            // Handle errors such as network issues, invalid response, etc.
            return ResponseEntity.status(500).body("Error fetching languages for the repository: " + e.getMessage());
        }
    }
}
