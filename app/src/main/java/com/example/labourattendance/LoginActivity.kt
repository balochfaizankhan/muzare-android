package com.example.labourattendance

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.labourattendance.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val initialPaddingLeft = binding.root.paddingLeft
        val initialPaddingTop = binding.root.paddingTop
        val initialPaddingRight = binding.root.paddingRight
        val initialPaddingBottom = binding.root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(
                initialPaddingLeft + systemBars.left,
                initialPaddingTop + systemBars.top,
                initialPaddingRight + systemBars.right,
                initialPaddingBottom + systemBars.bottom
            )
            insets
        }

        // Check if user is already logged in
        if (auth.currentUser != null) {
            navigateToDashboard()
            return
        }

        binding.buttonLogin.setOnClickListener {
            val email = binding.editEmail.text.toString().trim()
            val password = binding.editPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.progressBar.visibility = View.VISIBLE
            binding.buttonLogin.isEnabled = false

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    fetchUserRoleAndNavigate()
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    binding.buttonLogin.isEnabled = true
                    Toast.makeText(this, getString(R.string.err_auth_failed) + ": ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun fetchUserRoleAndNavigate() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val role = document.getString("role") ?: "viewer"
                saveRoleLocally(role)
                navigateToDashboard()
            }
            .addOnFailureListener {
                saveRoleLocally("viewer")
                navigateToDashboard()
            }
    }

    private fun saveRoleLocally(role: String) {
        getSharedPreferences("Settings", MODE_PRIVATE).edit().putString("User_Role", role).apply()
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        
        // Trigger auto-restore if the local database is empty
        val dbHelper = DatabaseHelper(this)
        if (dbHelper.getAllLabours().isEmpty()) {
            intent.putExtra("AUTO_RESTORE", true)
        }

        startActivity(intent)
        finish()
    }
}
