package com.userhunter.app.checker

import com.userhunter.app.core.CheckResult

interface UsernameChecker {
    suspend fun check(username: String): CheckResult
}
