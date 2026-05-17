package com.example.fileshareR.enums;

public enum NotificationType {
    // Legacy
    SHARE,
    SYSTEM,

    // User-facing
    DOCUMENT_DOWNLOADED,          // Someone downloaded the user's document
    GROUP_JOIN_APPROVED,          // User's join request was approved
    GROUP_JOIN_REJECTED,          // User's join request was rejected
    GROUP_ROLE_CHANGED,           // Member's role within a group changed
    GROUP_KICKED,                 // User was kicked from a group by group admin
    USER_BANNED_BY_PLATFORM,      // System admin banned the user (popup-realtime)

    // Group-admin facing
    GROUP_JOIN_REQUEST,           // Someone requested to join group (approval-required group)
    GROUP_DOC_PENDING_REVIEW,     // Document uploaded into group needs admin review

    // Platform-admin facing
    PLATFORM_GROUP_CREATED,       // A group was just created
    PLATFORM_PLAN_UPGRADED,       // A user upgraded their plan
    PLATFORM_ADDON_PURCHASED,     // A user / group owner bought a storage add-on

    // Group-owner facing (popup-realtime)
    GROUP_DELETED_BY_PLATFORM     // Platform admin deleted/banned this group
}
