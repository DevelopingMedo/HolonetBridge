package de.medo.holonetbridge.gitea;

/**
 * Request body for {@code POST /api/v1/repos/{owner}/{repo}/issues}.
 *
 * <p>Only the fields this adapter needs are modeled; Gitea's issue-creation endpoint accepts further optional
 * fields (assignees, labels, milestone, ...) that are not used here.
 */
record GiteaCreateIssueRequest(String title, String body) {}
