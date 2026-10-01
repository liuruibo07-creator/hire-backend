package com.hire.model.dto;

/** Recipient IDs stay in the broker envelope and are not sent as subscription controls. */
public record ChatBroadcast(long seekerId, long employerId, ChatEvent event) { }
