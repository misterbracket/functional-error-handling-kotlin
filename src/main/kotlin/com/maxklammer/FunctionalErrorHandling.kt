@file:Suppress("ktlint:standard:function-naming")

package com.maxklammer

import arrow.core.*

// <editor-fold desc="Setup and Data">
data class Job(
    val id: JobId,
    val company: Company,
    val role: Role,
    val salary: Salary,
)

// @JvmInline and value classes are used to create a type-safe wrapper around primitive types
// and avoid boxing overhead on primitive types.
@JvmInline
value class JobId(
    val value: Long,
)

@JvmInline
value class Company(
    val name: String,
)

@JvmInline
value class Role(
    val name: String,
)

@JvmInline
value class Salary(
    val value: Double,
) {
    operator fun compareTo(other: Salary): Int = value.compareTo(other.value)
}

val JOBS_DATABASE: Map<JobId, Job> =
    mapOf(
        JobId(1) to
            Job(
                JobId(1),
                Company("Apple, Inc."),
                Role("Software Engineer"),
                Salary(70_000.00),
            ),
        JobId(2) to
            Job(
                JobId(2),
                Company("Microsoft"),
                Role("Software Engineer"),
                Salary(80_000.00),
            ),
        JobId(3) to
            Job(
                JobId(3),
                Company("Google"),
                Role("Software Engineer"),
                Salary(90_000.00),
            ),
    )
// </editor-fold>

/*
 * Fictitious job application
 */

// <editor-fold desc="Application">

sealed interface JobError {
    data class JobNotFound(
        val id: JobId,
    ) : JobError

    data class GenericError(
        val cause: String,
    ) : JobError
}

interface Jobs {
    fun findById(id: JobId): Result<Job?>

    fun findByIdE(id: JobId): Either<JobError, Job>

    fun findAll(): Result<List<Job>>

    fun findAllE(): Either<JobError, List<Job>>
}

class LiveJobs : Jobs {
    override fun findById(id: JobId): Result<Job?> =
        runCatching {
            JOBS_DATABASE[id]
        }

    override fun findByIdE(id: JobId): Either<JobError, Job> =
        Either
            .catch {
                JOBS_DATABASE[id]
            }.mapLeft { e ->
                // any left value is a failure/exception when fetching the job
                JobError.GenericError(e.message ?: "Unknown error")
            }.flatMap {
                // if there is a value, return it else return a failure
                it?.right() ?: JobError.JobNotFound(id).left()
            }
//        try {
//            JOBS_DATABASE[id]?.right() ?: JobError.JobNotFound(id).left()
//        } catch (e: Exception) {
//            JobError.GenericError(e.message ?: "Unknown error").left()
//        }

    override fun findAll(): Result<List<Job>> = Result.success(JOBS_DATABASE.values.toList())

    override fun findAllE(): Either<JobError, List<Job>> = JOBS_DATABASE.values.toList().right()

    // Notice that this is the same functionality as the one above
    fun findById_OldStyle(id: JobId): Result<Job?> =
        try {
            Result.success(JOBS_DATABASE[id])
        } catch (e: Exception) {
            Result.failure(e)
        }
}

class JobService(
    private val jobs: Jobs,
    private val currencyConverter: CurrencyConverter,
) {
    fun maybePrintJob(id: JobId) {
        val maybeJob = jobs.findById(id)
        if (maybeJob.isSuccess) {
            maybeJob.getOrNull()?.apply {
                println("Job found: $this")
            }
                ?: println("Job not found")
        } else {
            println("Error: ${maybeJob.exceptionOrNull()}")
            // e.g. Log the exception
        }
    }

    fun getSalaryInEur(id: JobId): Result<Double> =
        jobs
            .findById(id)
            .map {
                it?.salary
            }.mapCatching {
                currencyConverter.convertToEur(it?.value)
            }

    fun getSalaryGapVsMaxSalaryNonIdiomatic(id: JobId): Result<Double> =
        runCatching {
            val maybeJob = jobs.findById(id).getOrThrow()
            val jobSalary = maybeJob?.salary ?: Salary(0.0)
            val allJobs = jobs.findAll().getOrThrow()
            val maxSalary = allJobs.maxSalary().getOrThrow()
            maxSalary.value - jobSalary.value
        }

    fun getSalaryGapVsMaxSalary(id: JobId): Result<Double> =
        jobs
            .findById(id)
            .flatMap { maybeJob ->
                val salary = maybeJob?.salary ?: Salary(0.0)
                jobs.findAll().flatMap { jobList ->
                    jobList.maxSalary().map { maxSalary ->
                        maxSalary.value - salary.value
                    }
                }
            }

    // same function = salary gap vs max salary with the Either type
    fun getSalaryGapVsMaxSalaryE(id: JobId): Either<JobError, Double> =
        jobs
            .findByIdE(id)
            .flatMap { job ->
                jobs.findAllE().flatMap { jobList ->
                    jobList.maxSalaryE().map { maxSalary ->
                        maxSalary.value - job.salary.value
                    }
                }
            }
}

class CurrencyConverter {
    fun convertToEur(amount: Double?): Double =
        if (amount != null && amount >= 0.0) {
            amount * 0.85
        } else {
            throw IllegalArgumentException("Amount must be present and non-negative")
        }
}

fun List<Job>.maxSalary(): Result<Salary> =
    runCatching {
        if (this.isEmpty()) {
            throw NoSuchElementException("No jobs present")
        } else {
            this.maxBy { it.salary.value }.salary
        }
    }

fun List<Job>.maxSalaryE(): Either<JobError, Salary> =
    if (this.isEmpty()) {
        JobError.GenericError("No jobs present").left()
    } else {
        this.maxBy { it.salary.value }.salary.right()
    }

// </editor-fold>

/*
 * Examples
 */

// <editor-fold desc="Examples">
// potentially failed computation

val appleJobResult: Result<Job> =
    Result.success(
        Job(
            JobId(2),
            Company("Apple, Inc."),
            Role("Software Engineer"),
            Salary(70_000.00),
        ),
    )
val notFoundJob: Result<Job> =
    Result.failure(
        NoSuchElementException("Job not found"),
    )

fun <T> T.toResult(): Result<T> = if (this is Throwable) Result.failure(this) else Result.success(this)

val allResult =
    Job(
        JobId(1),
        Company("Apple, Inc."),
        Role("Software Engineer"),
        Salary(70_000.00),
    ).toResult()

// transformation to get the salary on the result

// map
val appleJobSalary = appleJobResult.map { it.salary }
val appleSalaryCatching = appleJobResult.mapCatching { it.salary }

/*
 * Either type
 */

val appleJobEither: Either<JobError, Job> =
    Either.Right(
        JOBS_DATABASE[JobId(1)]!!,
    )

// extension function to convert to Either
val appleJobEither_v2: Either<JobError, Job> = JOBS_DATABASE[JobId(2)]!!.right()
// getOrNull, getOrElse, getOrHandle
// map, flatMap, mapCatching

// </editor-fold>

fun main() {
    val jobs = LiveJobs()
    val currencyConverter = CurrencyConverter()
    val jobService = JobService(jobs, currencyConverter)
    jobService.maybePrintJob(JobId(42))
    val maybeSalary = jobService.getSalaryInEur(JobId(42))

    // recover from error
    // You can check if the result is a success or failure
    // and provide a default value in case of failure
    val recovered =
        maybeSalary.recover {
            when (it) {
                is IllegalArgumentException -> 0.0
                else -> throw it
            }
        }

    // fold
    val finalStatement =
        recovered.fold(
            onSuccess = { "Salary in EUR: $it" },
            onFailure = {
                when (it) {
                    is IllegalArgumentException -> println("Amount must be present and non-negative")
                    else -> println("Error: ${it.message}")
                }
                "Job not found so we have to pay you 0.0 EUR"
            },
        )

    println(recovered)
    println(finalStatement)

    // -----
    println("---- salary gap Either ------")
    val salaryGap = jobService.getSalaryGapVsMaxSalaryE(JobId(2))
    salaryGap.fold(
        { println("Error: $it") },
        { println("Salary gap: $it") },
    )
}
