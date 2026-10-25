package de.medo.holonetbridge.gitea;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The subset of Gitea's issue representation this adapter reads from responses.
 *
 * <p>{@code number} is the per-repository issue number (shown in Gitea's UI and used in its
 * {@code /issues/{index}} endpoints), as opposed to {@code id}, which is a global, cross-repository identifier.
 * {@link de.medo.holonetbridge.commons.ticket.TicketDto#id()} is populated from {@code number}, since that is
 * what future read/edit/comment calls against this same repository need to address the issue again.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record GiteaIssueResponse(long number, String title, String body) {}
