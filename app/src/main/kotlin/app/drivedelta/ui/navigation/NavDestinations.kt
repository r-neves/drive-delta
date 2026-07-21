package app.drivedelta.ui.navigation

/**
 * Navigation routes. The graph has two levels: the outer graph ([AUTH], [MAIN], [CAR_EDIT]) and the
 * bottom-nav tabs inside [MAIN] ([DASHBOARD], [CARS]). More tabs (Places, History) arrive later.
 */
object NavDestinations {
    // Outer graph
    const val AUTH = "auth"
    const val MAIN = "main"

    // Bottom-nav tabs (inside MAIN)
    const val DASHBOARD = "dashboard"
    const val CARS = "cars"
    const val PLACES = "places"

    // Full-screen live tracking (outer graph; covers the bottom bar)
    const val TRACKING = "tracking"

    // Full-screen editors (outer graph)
    const val CAR_EDIT_ROUTE = "car_edit?${NavArgs.CAR_ID}={${NavArgs.CAR_ID}}"
    const val PLACE_EDIT_ROUTE = "place_edit?${NavArgs.PLACE_ID}={${NavArgs.PLACE_ID}}"

    /** Builds the car-edit route: no argument for add, a car id for edit. */
    fun carEdit(carId: String? = null): String =
        if (carId == null) "car_edit" else "car_edit?${NavArgs.CAR_ID}=$carId"

    /** Builds the place-edit route: no argument for add, a place id for edit. */
    fun placeEdit(placeId: String? = null): String =
        if (placeId == null) "place_edit" else "place_edit?${NavArgs.PLACE_ID}=$placeId"
}

/** Navigation argument keys. */
object NavArgs {
    const val CAR_ID = "carId"
    const val PLACE_ID = "placeId"
}
