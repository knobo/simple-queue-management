package com.example.simplequeue.domain.model

enum class MemberRole {
    GUEST,
    OPERATOR,
    OWNER;

    /**
     * Check if this role has at least the permissions of the required role.
     * OWNER > OPERATOR > GUEST
     */
    fun hasAtLeast(required: MemberRole): Boolean = this.ordinal >= required.ordinal
}
