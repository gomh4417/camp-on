package com.example.pleos_demo

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ParkingGate(context: Context) {
    @Volatile private var properties: CarPropertyManager?=null
    @Volatile private var restrictions: CarUxRestrictionsManager?=null
    private val car=Car.createCar(context,null,Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected,ready ->
        if(ready) {
            properties=connected.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            restrictions=connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        } else { properties=null;restrictions=null }
    }
    suspend fun read():ParkingState=withContext(Dispatchers.IO) {
        val parked=runCatching {
            properties?.getProperty(Int::class.javaObjectType,VehiclePropertyIds.GEAR_SELECTION,0)?.let {
                if(it.status==CarPropertyValue.STATUS_AVAILABLE) it.value==VehicleGear.GEAR_PARK else null
            }
        }.getOrNull()
        val unrestricted=runCatching { restrictions?.currentCarUxRestrictions?.let { !it.isRequiresDistractionOptimization } }.getOrNull()
        ParkingState(parked,unrestricted)
    }
    suspend fun nominalCapacityKwh():Double?=withContext(Dispatchers.IO) {
        runCatching {
            properties?.getProperty(Float::class.javaObjectType,VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY,0)?.let {
                if(it.status==CarPropertyValue.STATUS_AVAILABLE) it.value.toDouble().takeIf { v->v.isFinite() && v>0 }?.div(1000.0) else null
            }
        }.getOrNull()
    }
    fun close() { properties=null;restrictions=null;car?.disconnect() }
}
