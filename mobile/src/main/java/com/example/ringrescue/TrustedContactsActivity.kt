package com.example.ringrescue

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

data class Contact(val name: String, val phoneNumber: String, val imageUri: String?)

class ContactAdapter(
    context: Context,
    private val contacts: List<Contact>,
    private val onDeleteClick: (Contact) -> Unit
) : BaseAdapter() {
    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun getCount(): Int = contacts.size
    override fun getItem(position: Int): Any = contacts[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(R.layout.item_contact, parent, false)
        val contact = contacts[position]

        val nameText: TextView = view.findViewById(R.id.contactName)
        val phoneText: TextView = view.findViewById(R.id.contactPhone)
        val imageView: ImageView = view.findViewById(R.id.contactImage)
        val deleteButton: ImageView = view.findViewById(R.id.btnDeleteContact)

        nameText.text = contact.name
        phoneText.text = contact.phoneNumber
        
        if (!contact.imageUri.isNullOrEmpty()) {
            try {
                imageView.setImageURI(Uri.parse(contact.imageUri))
            } catch (e: Exception) {
                imageView.setImageResource(android.R.drawable.ic_menu_report_image)
            }
        } else {
            imageView.setImageResource(android.R.drawable.ic_menu_report_image)
        }

        deleteButton.setOnClickListener {
            onDeleteClick(contact)
        }

        return view
    }
}

class TrustedContactsActivity : AppCompatActivity() {

    private lateinit var contactsListView: ListView
    private lateinit var emptyStateText: TextView
    private lateinit var btnAddContact: FloatingActionButton
    private lateinit var btnSOS: Button
    
    // Footer views
    private lateinit var footerNavigation: View
    private lateinit var footerDeviceInfo: View
    private lateinit var footerTrustedContacts: View

    private val contactsList = mutableListOf<Contact>()
    private lateinit var adapter: ContactAdapter
    private lateinit var wearService: PhoneWearService
    private lateinit var sosManager: SosManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            pickContact()
        } else {
            Toast.makeText(this, "Contacts permission required to add trusted contacts", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickContactLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri = result.data?.data ?: return@registerForActivityResult
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
            )
            
            contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
                    
                    if (nameIndex != -1 && phoneIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        val phone = cursor.getString(phoneIndex)
                        val photoUri = if (photoIndex != -1) cursor.getString(photoIndex) else null
                        addContact(Contact(name, phone, photoUri))
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trusted_contacts)

        contactsListView = findViewById(R.id.contactsListView)
        emptyStateText = findViewById(R.id.emptyStateText)
        btnAddContact = findViewById(R.id.btnAddContact)
        btnSOS = findViewById(R.id.btnSOS)
        
        footerNavigation = findViewById(R.id.footer_navigation)
        footerDeviceInfo = findViewById(R.id.footer_device_info)
        footerTrustedContacts = findViewById(R.id.footer_trusted_contacts)

        wearService = PhoneWearService(this)
        sosManager = SosManager(this)

        adapter = ContactAdapter(this, contactsList) { contact ->
            removeContact(contact)
        }
        contactsListView.adapter = adapter

        loadContacts()
        updateUI()
        setupFooter()

        btnAddContact.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                pickContact()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }

        btnSOS.setOnClickListener {
            sosManager.sendSos()
        }
    }

    private fun pickContact() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        pickContactLauncher.launch(intent)
    }

    private fun setupFooter() {
        // Highlight active tab icon color
        findViewById<ImageView>(R.id.footer_trusted_contacts_icon).setColorFilter(getColor(R.color.primary_red))

        footerNavigation.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }
        footerDeviceInfo.setOnClickListener {
            showDeviceInfo()
        }
        footerTrustedContacts.setOnClickListener {
            // Already here
        }
    }

    private fun showDeviceInfo() {
        scope.launch {
            val isConnected = wearService.isWatchConnected()
            val batteryLevel = wearService.getWatchBatteryLevel()
            
            val message = if (isConnected) {
                "Watch is connected.\nBattery Level: ${batteryLevel ?: "Unknown"}%"
            } else {
                "Watch is disconnected."
            }

            AlertDialog.Builder(this@TrustedContactsActivity)
                .setTitle("Device Information")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun loadContacts() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val contactsJson = prefs.getString("trusted_contacts_v3", "[]")
        contactsList.clear()
        try {
            val jsonArray = JSONArray(contactsJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                contactsList.add(Contact(
                    obj.getString("name"),
                    obj.getString("phone"),
                    if (obj.isNull("photo")) null else obj.getString("photo")
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        adapter.notifyDataSetChanged()
    }

    private fun addContact(contact: Contact) {
        if (contactsList.none { it.phoneNumber == contact.phoneNumber }) {
            contactsList.add(contact)
            saveContacts()
            adapter.notifyDataSetChanged()
            updateUI()
        }
    }

    private fun removeContact(contact: Contact) {
        AlertDialog.Builder(this)
            .setTitle("Remove Contact")
            .setMessage("Are you sure you want to remove ${contact.name} from your trusted contacts?")
            .setPositiveButton("Remove") { _, _ ->
                contactsList.remove(contact)
                saveContacts()
                adapter.notifyDataSetChanged()
                updateUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveContacts() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val jsonArray = JSONArray()
        for (contact in contactsList) {
            val obj = JSONObject()
            obj.put("name", contact.name)
            obj.put("phone", contact.phoneNumber)
            obj.put("photo", contact.imageUri ?: JSONObject.NULL)
            jsonArray.put(obj)
        }
        prefs.edit().putString("trusted_contacts_v3", jsonArray.toString()).apply()
    }

    private fun updateUI() {
        if (contactsList.isEmpty()) {
            emptyStateText.visibility = View.VISIBLE
            contactsListView.visibility = View.GONE
        } else {
            emptyStateText.visibility = View.GONE
            contactsListView.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}