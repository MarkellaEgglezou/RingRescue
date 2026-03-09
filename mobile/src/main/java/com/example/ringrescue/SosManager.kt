package com.example.ringrescue

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.gms.location.LocationServices
import org.json.JSONArray

class SosManager(private val activity: Activity) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity)

    fun sendSos() {
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, missingPermissions.toTypedArray(), 1001)
            return
        }

        performSosAction()
    }

    private fun performSosAction() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val coords = if (location != null) {
                    "${location.latitude}, ${location.longitude}"
                } else {
                    "Unknown (Location disabled or not found)"
                }

                val message = "I might be in danger!\nLocation: $coords"
                val contacts = getTrustedContacts()

                if (contacts.isEmpty()) {
                    Toast.makeText(activity, "No trusted contacts found!", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val smsManager = activity.getSystemService(SmsManager::class.java)
                var sentCount = 0
                for (contact in contacts) {
                    try {
                        smsManager.sendTextMessage(contact.phoneNumber, null, message, null, null)
                        sentCount++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                Toast.makeText(activity, "SOS sent to $sentCount contacts!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(activity, "Permission denied for SOS", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getTrustedContacts(): List<Contact> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val contactsJson = prefs.getString("trusted_contacts_v3", "[]")
        val contacts = mutableListOf<Contact>()
        try {
            val jsonArray = JSONArray(contactsJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                contacts.add(Contact(
                    obj.getString("name"),
                    obj.getString("phone"),
                    if (obj.isNull("photo")) null else obj.getString("photo")
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return contacts
    }
}