package app.drivedelta.ui.history

import app.drivedelta.domain.model.Car
import app.drivedelta.domain.model.FuelType
import app.drivedelta.domain.model.Place
import app.drivedelta.domain.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripsOverviewBuilderTest {

    private val DAY = 86_400_000L // ms per day

    private fun trip(
        id: String, start: Long, durationMs: Long, hash: String,
        dist: Float = 10_000f, car: String? = "car", origin: String? = "p_home", dest: String? = "p_office",
    ) = Trip(
        id = id, userId = "u", startTime = start, endTime = start + durationMs,
        startLat = 0.0, startLng = 0.0, endLat = 0.0, endLng = 0.0,
        startPlaceId = origin, endPlaceId = dest, carId = car,
        distanceMeters = dist, durationMs = durationMs, routeHash = hash,
    )

    private val cars = listOf(
        Car("car", "u", "Model 3", "", FuelType.ELECTRIC, null, 60f, null, false, 0L),
    )
    private val places = listOf(
        Place("p_home", "u", "Home", "", 0.0, 0.0, 100f, "🏠", 0L),
        Place("p_office", "u", "Office", "", 0.0, 0.0, 100f, "🏢", 0L),
    )

    private fun build(trips: List<Trip>, carId: String? = null, query: String = "") =
        TripsOverviewBuilder.build(
            allTrips = trips, cars = cars, places = places,
            selectedCarId = carId, query = query,
            epochDayOf = { it / DAY },
        )

    @Test
    fun `recent items carry delta-vs-previous, PB events and resolved names`() {
        // Same route, three drives getting progressively faster → each beats the prior (2 PB events).
        val t1 = trip("t1", start = 1 * DAY, durationMs = 300_000, hash = "H")
        val t2 = trip("t2", start = 2 * DAY, durationMs = 280_000, hash = "H") // PB, 20s faster
        val t3 = trip("t3", start = 3 * DAY, durationMs = 260_000, hash = "H") // PB, 20s faster
        val o = build(listOf(t1, t2, t3))

        val items = o.sections.flatMap { it.items }.associateBy { it.tripId }
        assertEquals("Home", items["t1"]!!.originName)
        assertEquals("Office", items["t1"]!!.destName)
        assertEquals(FuelType.ELECTRIC, items["t1"]!!.fuelType)

        assertNull(items["t1"]!!.deltaVsPrevMs)   // first drive on route
        assertEquals(false, items["t1"]!!.isNewPb)
        assertEquals(-20_000L, items["t2"]!!.deltaVsPrevMs)
        assertTrue(items["t2"]!!.isNewPb)
        assertEquals(-20_000L, items["t3"]!!.deltaVsPrevMs)
        assertTrue(items["t3"]!!.isNewPb)

        // Stats over the whole set: 3 drives, 2 PB events, 30 km total.
        assertEquals(3, o.stats.drives)
        assertEquals(2, o.stats.newPbs)
        assertEquals(30f, o.stats.distanceKm, 0.01f)

        // Sections are day-grouped, newest day first.
        assertEquals(listOf(3L, 2L, 1L), o.sections.map { it.dayEpoch })
    }

    @Test
    fun `by-route groups distinct routes with best time and trend`() {
        val trips = listOf(
            // Route A: newest is a fresh PB → BEST/purple.
            trip("a1", 1 * DAY, 300_000, "A"),
            trip("a2", 2 * DAY, 280_000, "A"),
            // Route B (different dest): trending slower, best was the first drive.
            trip("b1", 1 * DAY, 200_000, "B", dest = "p_home"),
            trip("b2", 2 * DAY, 200_000, "B", dest = "p_home"),
            trip("b3", 3 * DAY, 230_000, "B", dest = "p_home"),
        )
        val routes = build(trips).routes.associateBy { it.destName }

        assertEquals(2, routes.size)
        assertEquals(280_000L, routes["Office"]!!.bestMs)
        assertEquals(RouteTrend.BEST, routes["Office"]!!.trend)      // newest set the record
        assertEquals(2, routes["Office"]!!.driveCount)

        assertEquals(200_000L, routes["Home"]!!.bestMs)
        assertEquals(RouteTrend.SLOWER, routes["Home"]!!.trend)    // newest slower than the window start
        assertEquals("b3", routes["Home"]!!.openTripId)           // opens the newest drive
        assertEquals(listOf(200_000L, 200_000L, 230_000L), routes["Home"]!!.series)
    }

    @Test
    fun `drives between the same places form one route even when every road hash differs`() {
        val trips = listOf(
            trip("t1", 1 * DAY, 300_000, "H1"),
            trip("t2", 2 * DAY, 280_000, "H2"),
            trip("t3", 3 * DAY, 290_000, "H3"),
            // The reverse direction is a different route.
            trip("r1", 4 * DAY, 310_000, "H4", origin = "p_office", dest = "p_home"),
        )
        val o = build(trips)
        assertEquals(2, o.routes.size)
        val out = o.routes.single { it.destName == "Office" }
        assertEquals(3, out.driveCount)
        assertEquals(280_000L, out.bestMs)
        val items = o.sections.flatMap { it.items }.associateBy { it.tripId }
        assertEquals(10_000L, items["t3"]!!.deltaVsPrevMs)
    }

    @Test
    fun `drives without both places fall back to the road hash`() {
        val trips = listOf(
            trip("a", 1 * DAY, 300_000, "SAME", origin = null, dest = null),
            trip("b", 2 * DAY, 280_000, "SAME", origin = null, dest = null),
            trip("c", 3 * DAY, 280_000, "", origin = null, dest = "p_office"), // no identity at all
        )
        val routes = build(trips).routes
        assertEquals(listOf("hash:SAME"), routes.map { it.routeKey })
        assertEquals(2, routes.single().driveCount)
    }

    @Test
    fun `vehicle filter and text search narrow the set`() {
        val other = cars + Car("car2", "u", "Golf", "", FuelType.PETROL, 50f, null, null, false, 0L)
        val trips = listOf(
            trip("t1", 1 * DAY, 300_000, "H", car = "car"),
            trip("t2", 2 * DAY, 280_000, "H2", car = "car2", dest = "p_gym"),
        )
        // Vehicle filter → only car2's drive.
        val filtered = TripsOverviewBuilder.build(trips, other, places, "car2", "", { it / DAY })
        assertEquals(1, filtered.stats.drives)
        assertEquals("t2", filtered.sections.single().items.single().tripId)

        // Search "office" matches t1's destination only.
        val searched = TripsOverviewBuilder.build(trips, other, places, null, "office", { it / DAY })
        assertEquals(1, searched.stats.drives)
        assertEquals("t1", searched.sections.single().items.single().tripId)
    }

    @Test
    fun `place-pair filter narrows recent, routes and reports available pairs`() {
        val trips = listOf(
            trip("t1", 1 * DAY, 300_000, "H", origin = "p_home", dest = "p_office"),
            trip("t2", 2 * DAY, 280_000, "H", origin = "p_home", dest = "p_office"),
            trip("t3", 3 * DAY, 200_000, "G", origin = "p_home", dest = "p_gym"),
        )

        // Available pairs: home→office (2 drives) and home→gym (1), sorted by count desc.
        val all = build(trips)
        assertEquals(2, all.pairOptions.size)
        assertEquals(RoutePair("p_home", "p_office"), all.pairOptions.first().pair)
        assertEquals(2, all.pairOptions.first().count)

        // Selecting home→office keeps only t1/t2, and only that route in By-route.
        val filtered = TripsOverviewBuilder.build(
            trips, cars, places, null, "", { it / DAY },
            selectedPair = RoutePair("p_home", "p_office"),
        )
        assertEquals(2, filtered.stats.drives)
        assertEquals(setOf("t1", "t2"), filtered.sections.flatMap { it.items }.map { it.tripId }.toSet())
        assertEquals(listOf("places:p_home>p_office"), filtered.routes.map { it.routeKey })
    }

    @Test
    fun `date-range filter narrows recent by start time but keeps route history for deltas`() {
        val trips = listOf(
            trip("t1", 1 * DAY, 300_000, "H"),
            trip("t2", 2 * DAY, 280_000, "H"), // PB vs t1
            trip("t3", 3 * DAY, 260_000, "H"), // PB vs t2
        )
        // Window covers only day 3.
        val o = TripsOverviewBuilder.build(
            trips, cars, places, null, "", { it / DAY },
            dateRange = (3 * DAY)..(3 * DAY + 1_000),
        )
        val items = o.sections.flatMap { it.items }
        assertEquals(listOf("t3"), items.map { it.tripId })
        // t3's delta still references t2 (the real previous drive), even though t2 is outside the window.
        assertEquals(-20_000L, items.single().deltaVsPrevMs)
        assertEquals(1, o.stats.drives)
    }

    @Test
    fun `trips with no route identity appear in recent but not by-route`() {
        // No hash and only one end named → no Trip.routeKey.
        val trips = listOf(trip("t1", 1 * DAY, 300_000, hash = "", dest = null))
        val o = build(trips)
        assertEquals(1, o.sections.flatMap { it.items }.size)
        assertNull(o.sections.single().items.single().deltaVsPrevMs)
        assertTrue(o.routes.isEmpty())
    }
}
