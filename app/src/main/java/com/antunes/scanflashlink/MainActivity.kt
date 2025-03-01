package com.antunes.scanflashlink

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalGetImage::class)
class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var textureView: TextureView
    private lateinit var txtScanResult: TextView
    private lateinit var edtServerIp: EditText
    private lateinit var btnSaveIp: Button
    private lateinit var sharedPreferences: SharedPreferences
    private var serverIp: String? = null
    private val SERVER_PORT = 12345
    private var isProcessing = false // Évite les scans en boucle

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialisation des vues
        textureView = findViewById(R.id.cameraPreview)
        txtScanResult = findViewById(R.id.txtScanResult)
        edtServerIp = findViewById(R.id.edtServerIp)
        btnSaveIp = findViewById(R.id.btnSaveIp)

        // Récupération et affichage de l'IP enregistrée
        sharedPreferences = getSharedPreferences("ScanFlashLinkPrefs", MODE_PRIVATE)
        serverIp = sharedPreferences.getString("SERVER_IP", "")
        edtServerIp.setText(serverIp)

        btnSaveIp.setOnClickListener {
            val ip = edtServerIp.text.toString().trim()
            if (ip.isNotEmpty()) {
                serverIp = ip
                sharedPreferences.edit().putString("SERVER_IP", ip).apply()
                Toast.makeText(this, "IP enregistrée : $ip", Toast.LENGTH_SHORT).show()
                Log.d("ScanFlashLink", "IP du serveur mise à jour: $ip")
            } else {
                Toast.makeText(this, "Veuillez entrer une IP valide", Toast.LENGTH_SHORT).show()
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Vérification et demande de permission pour la caméra
        if (checkCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    /** Vérifie si la permission de la caméra est accordée **/
    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    /** Demande l'autorisation d'utiliser la caméra **/
    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
    }

    /** Gère la réponse de l'utilisateur à la demande de permission **/
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                restartApp()
            } else {
                Toast.makeText(this, "Permission de la caméra refusée", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Redémarre l'application après l'octroi de la permission **/
    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider { request ->
                    val surfaceTexture = textureView.surfaceTexture
                    if (surfaceTexture != null) {
                        val surface = Surface(surfaceTexture)
                        request.provideSurface(surface, ContextCompat.getMainExecutor(this)) {}
                    }
                }
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("CameraX", "Erreur de liaison de la caméra", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImage(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        try {
            val image = imageProxy.image
            if (image != null) {
                val inputImage = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
                val scanner = BarcodeScanning.getClient()

                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.let { scanResult ->
                                isProcessing = true // Bloquer les scans successifs
                                txtScanResult.text = "Scanné : $scanResult"

                                sendToServer(scanResult)

                                val bitmap = textureView.bitmap
                                if (bitmap != null) {
                                    showScannedScreen(bitmap)
                                }
                            }
                        }
                    }
                    .addOnFailureListener {
                        Log.e("CameraX", "Erreur lors du scan du code-barres")
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showScannedScreen(bitmap: Bitmap) {
        runOnUiThread {
            val dialogView = layoutInflater.inflate(R.layout.dialog_scanned_screen, null)
            val imageView = dialogView.findViewById<ImageView>(R.id.scannedImageView)
            val btnOk = dialogView.findViewById<Button>(R.id.btnOk)

            imageView.setImageBitmap(bitmap)

            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create()

            btnOk.setOnClickListener {
                dialog.dismiss()
                isProcessing = false
            }

            dialog.show()
        }
    }

    private fun sendToServer(code: String) {
        if (serverIp.isNullOrEmpty()) return

        Thread {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(serverIp, SERVER_PORT), 5000)

                val outputStream = PrintWriter(OutputStreamWriter(socket.getOutputStream()), true)
                outputStream.println(code)
                outputStream.flush()
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
