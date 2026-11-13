package com.lrlr.codaeri.car

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DoorLockStateListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.content.Context
import android.util.Log
import com.lrlr.codaeri.domain.CarSignals
import com.lrlr.codaeri.domain.GearState
import com.lrlr.codaeri.domain.Signals
import com.lrlr.codaeri.domain.VehicleCheck
import ai.pleos.playground.vehicle.constant.control.LightState
import com.lrlr.codaeri.domain.WindowReading
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import com.lrlr.codaeri.R
import com.lrlr.codaeri.domain.text

class PleosCarSignals(context: Context) : CarSignals {

    private val vehicle = Vehicle(context.applicationContext)
    private var initialized = false

    override val gear = MutableStateFlow(GearState.UNKNOWN)
    override val driverSeated = MutableStateFlow<Boolean?>(null)
    override val passengerSeats = MutableStateFlow<List<Boolean?>>(emptyList())

    /** 운전석이 아닌 자리. 차에 없는 자리는 구독이 실패하고 값이 null로 남는다. */
    private val otherSeats = listOf(SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT)
    private val otherSeatListeners = otherSeats.mapIndexed { index, _ ->
        object : SeatOccupancyListener {
            override fun onSeatOccupancyUpdated(occupied: Boolean?) = setPassenger(index, occupied)
            override fun onFailed(e: Exception) = setPassenger(index, null)
        }
    }
    private fun setPassenger(index: Int, occupied: Boolean?) {
        passengerSeats.update { old -> List(otherSeats.size) { i -> if (i == index) occupied else old.getOrNull(i) } }
    }
    override val lockChanges = MutableSharedFlow<Boolean?>(extraBufferCapacity = 8)
    override val failures = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private val doors = listOf(DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT)

    private val windowNames get() = linkedMapOf(
        WindowValue.ROW_1_LEFT to text(R.string.car_window_driver),
        WindowValue.ROW_1_RIGHT to text(R.string.car_window_passenger),
        WindowValue.ROW_2_LEFT to text(R.string.car_window_rear_left),
        WindowValue.ROW_2_RIGHT to text(R.string.car_window_rear_right),
        WindowValue.ROOF_TOP_1 to text(R.string.car_window_sunroof),
    )

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            this@PleosCarSignals.gear.value = mapGear(gear)
        }
        override fun onFailed(e: Exception) {
            Log.w(TAG, "gear subscription failed", e)
            gear.value = GearState.UNAVAILABLE
            failures.tryEmit(text(R.string.car_signal_gear))
        }
    }

    private val seatListener = object : SeatOccupancyListener {
        override fun onSeatOccupancyUpdated(occupied: Boolean?) {
            driverSeated.value = occupied
        }
        override fun onFailed(e: Exception) {
            Log.w(TAG, "seat subscription failed", e)
            driverSeated.value = null
        }
    }

    // 에뮬레이터 확인(11-10): 문 잠금은 문별 구역을 지원하지 않고("area not supported")
    // 네 문을 묶은 구역 하나로만 읽힌다. 값 하나가 "네 문 모두 잠김" 여부다.
    private val allDoors get() = DoorArea(*doors.toTypedArray())
    private val lockListener = object : DoorLockStateListener {
        override fun onDoorLockStateUpdated(locked: Boolean?) {
            lockChanges.tryEmit(locked)
        }
        override fun onFailed(e: Exception) {
            Log.w(TAG, "door lock subscription failed", e)
        }
    }

    override fun start() {
        if (initialized) return
        try {
            vehicle.initialize()
            initialized = true
        } catch (e: Exception) {
            Log.w(TAG, "vehicle init failed", e)
            gear.value = GearState.UNAVAILABLE
            failures.tryEmit(text(R.string.car_signal_vehicle))
            return
        }
        // 기어 값이 끝내 오지 않으면 "기어 신호 없음"으로 보고 마무리 버튼을 막지 않는다
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (gear.value == GearState.UNKNOWN) {
                gear.value = GearState.UNAVAILABLE
                failures.tryEmit(text(R.string.car_signal_gear))
            }
        }, 5_000)
        safe("gear") {
            vehicle.getGear().getCurrentGearSelectionState({ gear.value = mapGear(it) }, {
                gear.value = GearState.UNAVAILABLE
            })
            vehicle.getGear().registerGearSelection(gearListener)
        }
        safe("seat") {
            val driver = SeatArea(SeatValue.ROW_1_LEFT)
            vehicle.getSeat().getCurrentSeatOccupancy(driver, { driverSeated.value = it }, {})
            vehicle.getSeat().registerSeatOccupancy(driver, seatListener)
        }
        otherSeats.forEachIndexed { index, seat ->
            safe("passenger seat") {
                val area = SeatArea(seat)
                vehicle.getSeat().getCurrentSeatOccupancy(area, { setPassenger(index, it) }, {})
                vehicle.getSeat().registerSeatOccupancy(area, otherSeatListeners[index])
            }
        }
        safe("door lock") {
            vehicle.getDoor().registerDoorLockState(allDoors, lockListener)
        }
    }

    override fun stop() {
        if (!initialized) return
        safe("unregister") {
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.getSeat().unregisterSeatOccupancy(SeatArea(SeatValue.ROW_1_LEFT), seatListener)
            otherSeats.forEachIndexed { index, seat -> vehicle.getSeat().unregisterSeatOccupancy(SeatArea(seat), otherSeatListeners[index]) }
            vehicle.getDoor().unregisterDoorLockState(allDoors, lockListener)
        }
        safe("release") { vehicle.release() }
        initialized = false
    }

    override suspend fun odometerKm(): Float? {
        if (!initialized) return null
        val d = CompletableDeferred<Float?>()
        safe("odometer") {
            vehicle.getOdometer().getCurrentDistanceDriven({ d.complete(it) }, { d.complete(null) })
        }
        return Signals.odometer(withTimeoutOrNull(TIMEOUT_MS) { d.await() })
    }

    override suspend fun windows(): List<WindowReading> {
        if (!initialized) return emptyList()
        val bounds = CompletableDeferred<Map<WindowValue, Int>>()
        safe("window capability") {
            vehicle.getWindow().checkWindowPositionCapabilities({ map: Map<WindowArea, ai.pleos.playground.vehicle.api.model.capability.AreaBounds<Int>> ->
                bounds.complete(
                    map.flatMap { (area, b) -> area.values.map { it to (b.minValue ?: 0) } }.toMap(),
                )
            }, { bounds.complete(emptyMap()) })
        }
        val supported = withTimeoutOrNull(TIMEOUT_MS) { bounds.await() } ?: emptyMap()
        return windowNames.filterKeys { it in supported }.map { (value, name) ->
            val pos = CompletableDeferred<Int?>()
            safe("window $value") {
                vehicle.getWindow().getCurrentWindowPosition(WindowArea(value), { pos.complete(it) }, { pos.complete(null) })
            }
            WindowReading(name, withTimeoutOrNull(TIMEOUT_MS) { pos.await() }, supported[value] ?: 0)
        }
    }

    override suspend fun doorLocks(): List<Boolean?> {
        if (!initialized) return emptyList()
        val v = CompletableDeferred<Boolean?>()
        safe("door lock") {
            vehicle.getDoor().getDoorLockState(allDoors, { v.complete(it) }, { v.complete(null) })
        }
        return listOf(withTimeoutOrNull(TIMEOUT_MS) { v.await() })
    }

    override suspend fun vehicleCheck(now: Long): VehicleCheck {
        if (!initialized) return VehicleCheck(at = now)
        return VehicleCheck(
            at = now,
            lightsOn = lights(),
            openDoors = openDoors(),
            fuelPercent = readInt { ok, fail -> vehicle.getFuel().getFuelPercent(ok, fail) },
            batteryPercent = readInt { ok, fail -> vehicle.getEvBattery().getEvBatteryStateOfCharge(ok, fail) },
        )
    }

    private suspend fun lights(): List<String>? {
        val light = vehicle.getLight()
        val reads = listOf(
            text(R.string.car_light_low_beam) to readLight { ok, fail -> light.getCurrentLowBeamLightState(ok, fail) },
            text(R.string.car_light_high_beam) to readLight { ok, fail -> light.getCurrentHighBeamLightState(ok, fail) },
            text(R.string.car_light_tail) to readLight { ok, fail -> light.getCurrentTailLampLightState(ok, fail) },
            text(R.string.car_light_cabin) to readLight { ok, fail -> light.getCurrentCabinLightState(ok, fail) },
        )
        if (reads.all { it.second == null }) return null
        // 주간주행등은 시동 중 자동으로 켜지므로 "켜짐"으로 적지 않는다
        return reads.filter { it.second == LightState.ON }.map { it.first }
    }

    private suspend fun readLight(call: ((LightState?) -> Unit, (Exception) -> Unit) -> Unit): LightState? {
        val d = CompletableDeferred<LightState?>()
        safe("light") { call({ d.complete(it) }, { d.complete(null) }) }
        return withTimeoutOrNull(TIMEOUT_MS) { d.await() }
    }

    private suspend fun readInt(call: ((Int?) -> Unit, (Exception) -> Unit) -> Unit): Int? {
        val d = CompletableDeferred<Int?>()
        safe("int read") { call({ d.complete(it) }, { d.complete(null) }) }
        return withTimeoutOrNull(TIMEOUT_MS) { d.await() }?.takeIf { it in 0..100 }
    }

    private suspend fun openDoors(): List<String>? {
        val names = linkedMapOf(
            DoorValue.ROW_1_LEFT to text(R.string.car_door_driver),
            DoorValue.ROW_1_RIGHT to text(R.string.car_door_passenger),
            DoorValue.ROW_2_LEFT to text(R.string.car_door_rear_left),
            DoorValue.ROW_2_RIGHT to text(R.string.car_door_rear_right),
            DoorValue.REAR to text(R.string.car_door_trunk),
            DoorValue.HOOD to text(R.string.car_door_hood),
        )
        val bounds = CompletableDeferred<Map<DoorValue, Int>>()
        safe("door capability") {
            vehicle.getDoor().checkDoorPositionCapabilities({ map: Map<DoorArea, ai.pleos.playground.vehicle.api.model.capability.AreaBounds<Int>> ->
                bounds.complete(map.flatMap { (area, b) -> area.values.map { it to (b.minValue ?: 0) } }.toMap())
            }, { bounds.complete(emptyMap()) })
        }
        val supported = withTimeoutOrNull(TIMEOUT_MS) { bounds.await() } ?: emptyMap()
        val readings = names.filterKeys { it in supported }.map { (value, name) ->
            val pos = CompletableDeferred<Int?>()
            safe("door pos $value") {
                vehicle.getDoor().getCurrentDoorPosition(DoorArea(value), { pos.complete(it) }, { pos.complete(null) })
            }
            WindowReading(name, withTimeoutOrNull(TIMEOUT_MS) { pos.await() }, supported[value] ?: 0)
        }
        if (readings.none { it.position != null }) return null
        return Signals.windowState(readings).openWindows
    }

    private inline fun safe(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "$what failed", e)
        }
    }

    private fun mapGear(g: VehicleGear?): GearState = when (g) {
        null, VehicleGear.GEAR_UNKNOWN -> GearState.UNKNOWN
        VehicleGear.GEAR_PARK -> GearState.PARK
        VehicleGear.GEAR_REVERSE -> GearState.REVERSE
        else -> GearState.NOT_PARK
    }

    companion object {
        private const val TAG = "DaeriCar"
        private const val TIMEOUT_MS = 2_000L
    }
}
