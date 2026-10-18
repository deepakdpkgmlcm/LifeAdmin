package com.lifeadmin.app.data

/** One-tap suggestions shown when adding a new item. */
data class Template(
    val title: String,
    val category: Category,
    val cycle: Cycle = Cycle.NONE,
    val autoRenew: Boolean = false,
    val every: Int = 1,
    /** Time of day in minutes, or null for all day. */
    val minute: Int? = null,
    /** Weekly repeats: ISO weekdays. */
    val days: String = "",
    val alarm: Boolean = false,
)

object Templates {
    private fun h(hour: Int, min: Int = 0) = hour * 60 + min

    val all: List<Template> = listOf(
        // Tasks & everyday reminders
        Template("Drink water", Category.HEALTH, Cycle.HOURS, true, every = 2, minute = h(9)),
        Template("Stand up and stretch", Category.HEALTH, Cycle.MINUTES, true, every = 45, minute = h(10)),
        Template("Take vitamins", Category.HEALTH, Cycle.DAILY, true, minute = h(8, 30)),
        Template("Workout", Category.HEALTH, Cycle.WEEKLY, true, minute = h(7), days = "1,3,5"),
        Template("Wake up", Category.PERSONAL, Cycle.WEEKLY, true, minute = h(6, 30), days = "1,2,3,4,5", alarm = true),
        Template("Call family", Category.PERSONAL, Cycle.WEEKLY, true, minute = h(19), days = "7"),
        Template("Birthday", Category.PERSONAL, Cycle.YEARLY, true),
        Template("Daily stand-up", Category.WORK, Cycle.WEEKLY, true, minute = h(10), days = "1,2,3,4,5"),
        Template("Submit timesheet", Category.WORK, Cycle.WEEKLY, true, minute = h(16), days = "5"),
        Template("Meeting", Category.WORK, minute = h(11)),
        Template("Buy groceries", Category.TASK, minute = h(18)),
        Template("Pick up parcel", Category.TASK, minute = h(17)),
        Template("Pay someone back", Category.TASK),
        Template("Water the plants", Category.HOME, Cycle.DAILY, true, every = 3, minute = h(8)),
        Template("Take out the trash", Category.HOME, Cycle.WEEKLY, true, minute = h(20), days = "2,5"),
        Template("Water filter change", Category.HOME, Cycle.MONTHLY, true, every = 3),
        Template("AC service", Category.HOME, Cycle.YEARLY),
        Template("Smoke alarm battery", Category.HOME, Cycle.YEARLY),

        // Documents
        Template("Passport", Category.DOCUMENT),
        Template("Driving licence", Category.DOCUMENT),
        Template("Visa / residence permit", Category.DOCUMENT),
        Template("National ID card", Category.DOCUMENT),
        Template("Professional licence", Category.DOCUMENT),

        // Subscriptions
        Template("Netflix", Category.SUBSCRIPTION, Cycle.MONTHLY, autoRenew = true),
        Template("Spotify", Category.SUBSCRIPTION, Cycle.MONTHLY, autoRenew = true),
        Template("YouTube Premium", Category.SUBSCRIPTION, Cycle.MONTHLY, autoRenew = true),
        Template("Amazon Prime", Category.SUBSCRIPTION, Cycle.YEARLY, autoRenew = true),
        Template("ChatGPT Plus", Category.SUBSCRIPTION, Cycle.MONTHLY, autoRenew = true),
        Template("Google One", Category.SUBSCRIPTION, Cycle.MONTHLY, autoRenew = true),
        Template("Gym membership", Category.SUBSCRIPTION, Cycle.MONTHLY, autoRenew = true),
        Template("Domain name", Category.SUBSCRIPTION, Cycle.YEARLY, autoRenew = true),

        // Bills
        Template("Credit card bill", Category.BILL, Cycle.MONTHLY),
        Template("Electricity bill", Category.BILL, Cycle.MONTHLY),
        Template("Phone bill", Category.BILL, Cycle.MONTHLY),
        Template("Internet bill", Category.BILL, Cycle.MONTHLY),
        Template("Rent", Category.BILL, Cycle.MONTHLY),
        Template("Loan EMI", Category.BILL, Cycle.MONTHLY),

        // Insurance
        Template("Health insurance", Category.INSURANCE, Cycle.YEARLY),
        Template("Life insurance", Category.INSURANCE, Cycle.YEARLY),
        Template("Home insurance", Category.INSURANCE, Cycle.YEARLY),

        // Vehicle
        Template("Car insurance", Category.VEHICLE, Cycle.YEARLY),
        Template("Vehicle registration", Category.VEHICLE),
        Template("Emission / pollution test", Category.VEHICLE),
        Template("Vehicle service", Category.VEHICLE),

        // Warranties
        Template("Phone warranty", Category.WARRANTY),
        Template("Laptop warranty", Category.WARRANTY),
        Template("Appliance warranty", Category.WARRANTY),
    )

    fun forCategory(category: Category): List<Template> = all.filter { it.category == category }
}
