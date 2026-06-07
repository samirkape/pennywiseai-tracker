package com.pennywiseai.tracker.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Registry of selectable category icons.
 * Icons are stored in the database by [key] and resolved at display time.
 */
object CategoryIcons {

    const val DEFAULT_KEY = "category"

    data class IconEntry(
        val key: String,
        val icon: ImageVector,
    )

    fun getIcon(key: String?): ImageVector {
        if (key.isNullOrBlank()) return iconsByKey[DEFAULT_KEY] ?: Icons.Default.Category
        return iconsByKey[key] ?: iconsByKey[DEFAULT_KEY] ?: Icons.Default.Category
    }

    fun isValidKey(key: String?): Boolean = !key.isNullOrBlank() && iconsByKey.containsKey(key)

    fun resolveKey(categoryName: String, storedIcon: String? = null): String {
        if (isValidKey(storedIcon)) return storedIcon!!
        return defaultKeyForName(categoryName)
    }

    fun defaultKeyForName(categoryName: String): String {
        return defaultKeysByCategoryName[categoryName] ?: DEFAULT_KEY
    }

    val defaultKeysByCategoryName: Map<String, String> = mapOf(
        "Food & Dining" to "restaurant",
        "Groceries" to "shopping_cart",
        "Transportation" to "directions_car",
        "Shopping" to "shopping_bag",
        "Bills & Utilities" to "receipt",
        "Entertainment" to "movie_filter",
        "Healthcare" to "local_hospital",
        "Investments" to "trending_up",
        "Banking" to "account_balance",
        "Personal Care" to "face",
        "Education" to "school",
        "Mobile" to "smartphone",
        "Fitness" to "fitness_center",
        "Insurance" to "shield",
        "Travel" to "flight",
        "Salary" to "payments",
        "Income" to "add_circle",
        "Others" to "category",
        "Credit Card Payment" to "credit_card",
        "Tax" to "account_balance_wallet",
        "Bank Charges" to "money_off",
    )

    private val allIcons: List<IconEntry> = listOf(
        // Food & drink
        IconEntry("restaurant", Icons.Default.Restaurant),
        IconEntry("fastfood", Icons.Default.Fastfood),
        IconEntry("local_cafe", Icons.Default.LocalCafe),
        IconEntry("local_bar", Icons.Default.LocalBar),
        IconEntry("local_pizza", Icons.Default.LocalPizza),
        IconEntry("lunch_dining", Icons.Default.LunchDining),
        IconEntry("dinner_dining", Icons.Default.DinnerDining),
        IconEntry("breakfast_dining", Icons.Default.BreakfastDining),
        IconEntry("takeout_dining", Icons.Default.TakeoutDining),
        IconEntry("ramen_dining", Icons.Default.RamenDining),
        IconEntry("bakery_dining", Icons.Default.BakeryDining),
        IconEntry("icecream", Icons.Default.Icecream),
        IconEntry("cake", Icons.Default.Cake),
        IconEntry("coffee", Icons.Default.Coffee),
        IconEntry("wine_bar", Icons.Default.WineBar),
        IconEntry("local_grocery_store", Icons.Default.LocalGroceryStore),
        IconEntry("shopping_cart", Icons.Default.ShoppingCart),
        IconEntry("shopping_basket", Icons.Default.ShoppingBasket),
        // Transport
        IconEntry("directions_car", Icons.Default.DirectionsCar),
        IconEntry("commute", Icons.Default.Commute),
        IconEntry("directions_bus", Icons.Default.DirectionsBus),
        IconEntry("train", Icons.Default.Train),
        IconEntry("directions_bike", Icons.AutoMirrored.Filled.DirectionsBike),
        IconEntry("two_wheeler", Icons.Default.TwoWheeler),
        IconEntry("local_gas_station", Icons.Default.LocalGasStation),
        IconEntry("local_taxi", Icons.Default.LocalTaxi),
        IconEntry("flight", Icons.Default.Flight),
        IconEntry("airplanemode_active", Icons.Default.AirplanemodeActive),
        IconEntry("luggage", Icons.Default.Luggage),
        IconEntry("map", Icons.Default.Map),
        IconEntry("explore", Icons.Default.Explore),
        // Shopping
        IconEntry("shopping_bag", Icons.Default.ShoppingBag),
        IconEntry("store", Icons.Default.Store),
        IconEntry("storefront", Icons.Default.Storefront),
        IconEntry("local_mall", Icons.Default.LocalMall),
        IconEntry("inventory", Icons.Default.Inventory),
        IconEntry("card_giftcard", Icons.Default.CardGiftcard),
        // Bills & finance
        IconEntry("receipt", Icons.Default.Receipt),
        IconEntry("payment", Icons.Default.Payment),
        IconEntry("credit_card", Icons.Default.CreditCard),
        IconEntry("account_balance", Icons.Default.AccountBalance),
        IconEntry("account_balance_wallet", Icons.Default.AccountBalanceWallet),
        IconEntry("payments", Icons.Default.Payments),
        IconEntry("attach_money", Icons.Default.AttachMoney),
        IconEntry("monetization_on", Icons.Default.MonetizationOn),
        IconEntry("savings", Icons.Default.Savings),
        IconEntry("trending_up", Icons.AutoMirrored.Filled.TrendingUp),
        IconEntry("show_chart", Icons.AutoMirrored.Filled.ShowChart),
        IconEntry("money_off", Icons.Default.MoneyOff),
        IconEntry("add_circle", Icons.Default.AddCircle),
        IconEntry("remove_circle", Icons.Default.RemoveCircle),
        IconEntry("currency_rupee", Icons.Default.CurrencyRupee),
        // Home & living
        IconEntry("home", Icons.Default.Home),
        IconEntry("apartment", Icons.Default.Apartment),
        IconEntry("bed", Icons.Default.Bed),
        IconEntry("hotel", Icons.Default.Hotel),
        IconEntry("kitchen", Icons.Default.Kitchen),
        IconEntry("lightbulb", Icons.Default.Lightbulb),
        IconEntry("electric_bolt", Icons.Default.ElectricBolt),
        IconEntry("water_drop", Icons.Default.WaterDrop),
        IconEntry("local_laundry_service", Icons.Default.LocalLaundryService),
        IconEntry("cleaning_services", Icons.Default.CleaningServices),
        // Health & wellness
        IconEntry("local_hospital", Icons.Default.LocalHospital),
        IconEntry("health_and_safety", Icons.Default.HealthAndSafety),
        IconEntry("medical_services", Icons.Default.MedicalServices),
        IconEntry("local_pharmacy", Icons.Default.LocalPharmacy),
        IconEntry("spa", Icons.Default.Spa),
        IconEntry("face", Icons.Default.Face),
        IconEntry("self_improvement", Icons.Default.SelfImprovement),
        IconEntry("fitness_center", Icons.Default.FitnessCenter),
        IconEntry("sports_martial_arts", Icons.Default.SportsMartialArts),
        IconEntry("psychology", Icons.Default.Psychology),
        // Entertainment
        IconEntry("movie_filter", Icons.Default.MovieFilter),
        IconEntry("play_circle", Icons.Default.PlayCircle),
        IconEntry("music_note", Icons.Default.MusicNote),
        IconEntry("headphones", Icons.Default.Headphones),
        IconEntry("sports_esports", Icons.Default.SportsEsports),
        IconEntry("theater_comedy", Icons.Default.TheaterComedy),
        IconEntry("celebration", Icons.Default.Celebration),
        IconEntry("festival", Icons.Default.Festival),
        IconEntry("nightlife", Icons.Default.Nightlife),
        // Education & work
        IconEntry("school", Icons.Default.School),
        IconEntry("book", Icons.Default.Book),
        IconEntry("menu_book", Icons.Default.MenuBook),
        IconEntry("local_library", Icons.Default.LocalLibrary),
        IconEntry("science", Icons.Default.Science),
        IconEntry("work", Icons.Default.Work),
        IconEntry("business", Icons.Default.Business),
        IconEntry("engineering", Icons.Default.Engineering),
        IconEntry("construction", Icons.Default.Construction),
        IconEntry("handyman", Icons.Default.Handyman),
        // Tech & communication
        IconEntry("smartphone", Icons.Default.Smartphone),
        IconEntry("phone_android", Icons.Default.PhoneAndroid),
        IconEntry("laptop", Icons.Default.Laptop),
        IconEntry("computer", Icons.Default.Computer),
        IconEntry("tv", Icons.Default.Tv),
        IconEntry("wifi", Icons.Default.Wifi),
        IconEntry("router", Icons.Default.Router),
        IconEntry("cloud", Icons.Default.Cloud),
        IconEntry("email", Icons.Default.Email),
        IconEntry("call", Icons.Default.Call),
        IconEntry("chat", Icons.Default.Chat),
        // Insurance & security
        IconEntry("shield", Icons.Default.Shield),
        IconEntry("security", Icons.Default.Security),
        IconEntry("lock", Icons.Default.Lock),
        IconEntry("verified", Icons.Default.Verified),
        IconEntry("policy", Icons.Default.Policy),
        // Family & pets
        IconEntry("pets", Icons.Default.Pets),
        IconEntry("child_care", Icons.Default.ChildCare),
        IconEntry("family_restroom", Icons.Default.FamilyRestroom),
        IconEntry("stroller", Icons.Default.Stroller),
        IconEntry("toys", Icons.Default.Toys),
        IconEntry("groups", Icons.Default.Groups),
        // Sports & outdoors
        IconEntry("sports_soccer", Icons.Default.SportsSoccer),
        IconEntry("sports_tennis", Icons.Default.SportsTennis),
        IconEntry("hiking", Icons.Default.Hiking),
        IconEntry("pool", Icons.Default.Pool),
        IconEntry("beach_access", Icons.Default.BeachAccess),
        IconEntry("park", Icons.Default.Park),
        IconEntry("nature", Icons.Default.Nature),
        IconEntry("agriculture", Icons.Default.Agriculture),
        // Misc
        IconEntry("favorite", Icons.Default.Favorite),
        IconEntry("star", Icons.Default.Star),
        IconEntry("volunteer_activism", Icons.Default.VolunteerActivism),
        IconEntry("local_shipping", Icons.Default.LocalShipping),
        IconEntry("delivery_dining", Icons.Default.DeliveryDining),
        IconEntry("event", Icons.Default.Event),
        IconEntry("calendar_today", Icons.Default.CalendarToday),
        IconEntry("assignment", Icons.Default.Assignment),
        IconEntry("folder", Icons.Default.Folder),
        IconEntry("brush", Icons.Default.Brush),
        IconEntry("palette", Icons.Default.Palette),
        IconEntry("camera_alt", Icons.Default.CameraAlt),
        IconEntry("photo_camera", Icons.Default.PhotoCamera),
        IconEntry("diamond", Icons.Default.Diamond),
        IconEntry("emoji_events", Icons.Default.EmojiEvents),
        IconEntry("more_horiz", Icons.Default.MoreHoriz),
        IconEntry("category", Icons.Default.Category),
    )

    val pickerIcons: List<IconEntry> = allIcons

    private val iconsByKey: Map<String, ImageVector> = allIcons.associate { it.key to it.icon }
}
