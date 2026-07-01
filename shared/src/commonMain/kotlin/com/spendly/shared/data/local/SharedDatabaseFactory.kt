package com.spendly.shared.data.local

expect class SharedDatabaseFactory() {
    fun createDatabase(): SharedDatabase
}
