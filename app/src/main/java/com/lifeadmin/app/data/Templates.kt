package com.lifeadmin.app.data

/** One-tap suggestions shown when adding a new item. */
data class Template(
    val title: String,
    val category: Category,
    val cycle: Cycle = Cycle.NONE,
    val autoRenew: Boolean = false,
)

object Templates {
    val all: List<Template> = listOf(
        // Documents
        Template("Passport", Category.DOCUMENT),
        Template("Driving licence", Category.DOCUMENT),
        Template("Visa / residence permit", Category.DOCUMENT),
        Template("National ID card", Category.DOCUMENT),
        Template("Work permit", Category.DOCUMENT),
        Template("Professional licence", Category.DOCUMENT),

        // Subscriptions
        Template("Netflix", Category.SUBSCRIPTION, Cycle.MONTHLY, autoRenew = true),
        Template("Spotify", Category.SUBSCRIPTION, Cycle.MONTHLY, autoRenew = true),
        Template("YouTube Premium", Category.SUBSCRIPTION, Cycle.MONTHLY, autoRenew = true),
        Template("Amazon Prime", Category.SUBSCRIPTION, Cycle.YEARLY, autoRenew = true),
        Template("Disney+", Category.SUBSCRIPTION, Cycle.MONTHLY, autoRenew = true),
        Template("ChatGPT Plus", Category.SUBSCRIPTION, Cycle.MONTHLY, autoRenew = true),
        Template("Google One", Category.SUBSCRIPTION, Cycle.MONTHLY, autoRenew = true),
        Template("iCloud+", Category.SUBSCRIPTION, Cycle.MONTHLY, autoRenew = true),
        Template("Gym membership", Category.SUBSCRIPTION, Cycle.MONTHLY, autoRenew = true),
        Template("Domain name", Category.SUBSCRIPTION, Cycle.YEARLY, autoRenew = true),

        // Bills
        Template("Credit card bill", Category.BILL, Cycle.MONTHLY),
        Template("Electricity bill", Category.BILL, Cycle.MONTHLY),
        Template("Phone bill", Category.BILL, Cycle.MONTHLY),
        Template("Internet bill", Category.BILL, Cycle.MONTHLY),
        Template("Rent", Category.BILL, Cycle.MONTHLY),
        Template("Loan EMI", Category.BILL, Cycle.MONTHLY),
        Template("Property tax", Category.BILL, Cycle.YEARLY),

        // Insurance
        Template("Health insurance", Category.INSURANCE, Cycle.YEARLY),
        Template("Life insurance", Category.INSURANCE, Cycle.YEARLY),
        Template("Home insurance", Category.INSURANCE, Cycle.YEARLY),
        Template("Travel insurance", Category.INSURANCE),

        // Vehicle
        Template("Car insurance", Category.VEHICLE, Cycle.YEARLY),
        Template("Bike insurance", Category.VEHICLE, Cycle.YEARLY),
        Template("Vehicle registration", Category.VEHICLE),
        Template("Emission / pollution test", Category.VEHICLE),
        Template("Vehicle service", Category.VEHICLE),
        Template("Road tax", Category.VEHICLE, Cycle.YEARLY),

        // Warranties
        Template("Phone warranty", Category.WARRANTY),
        Template("Laptop warranty", Category.WARRANTY),
        Template("TV warranty", Category.WARRANTY),
        Template("Appliance warranty", Category.WARRANTY),

        // Home
        Template("Water filter change", Category.HOME, Cycle.QUARTERLY),
        Template("AC service", Category.HOME, Cycle.YEARLY),
        Template("Smoke alarm battery", Category.HOME, Cycle.YEARLY),
        Template("Fire extinguisher check", Category.HOME, Cycle.YEARLY),
        Template("Pest control", Category.HOME, Cycle.QUARTERLY),
    )

    fun forCategory(category: Category): List<Template> = all.filter { it.category == category }
}
