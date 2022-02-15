package com.example.notelite.ui

import android.Manifest
import android.app.Activity.RESULT_OK
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.android.synthetic.main.fragment_create_note.*
import kotlinx.coroutines.launch
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import java.text.SimpleDateFormat
import java.util.*
import android.R.attr.path
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import com.example.notelite.R
import com.example.notelite.database.NotesDatabase
import com.example.notelite.entities.Notes
import com.example.notelite.ui.viewmodels.CreateNoteViewModel
import com.example.notelite.util.NoteBottomSheetFragment
import com.example.notelite.ui.BaseFragment
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognizerOptions
import kotlinx.android.synthetic.main.fragment_create_note.imgMore
import kotlinx.android.synthetic.main.fragment_create_note.layoutImage
import kotlinx.android.synthetic.main.fragment_create_note.layoutWebUrl
import kotlinx.android.synthetic.main.fragment_notes_bottom_sheet.*
import java.lang.StringBuilder


class CreateNoteFragment : BaseFragment(),EasyPermissions.PermissionCallbacks,EasyPermissions.RationaleCallbacks,TextToSpeech.OnInitListener{

//    internal companion object {
//        private const val REQUEST_CODE_STT = 1
//    }

    var selectedColor = "#171C26"
    var currentDate:String? = null
    private var READ_STORAGE_PERM = 123
    private var REQUEST_CODE_IMAGE = 456
    private var selectedImagePath = ""
    private var webLink = ""
    private var noteId = -1
    private lateinit var imageBitmap:Bitmap
    private  val  REQUEST_IMAGE_CAPTURE=1
    private var tts: TextToSpeech? = null



    private val viewmodel : CreateNoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        noteId = requireArguments().getInt("noteId",-1)
        tts = TextToSpeech(requireActivity(), this)

    }



    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_create_note, container, false)
    }

    companion object {
        private const val REQUEST_CODE_STT = 2
        @JvmStatic
        fun newInstance() =
                CreateNoteFragment().apply {
                    arguments = Bundle().apply {
                    }
                }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        shareNote()

        if (noteId != -1){

            launch {
                context?.let {
                    var notes = NotesDatabase.getDatabase(it).noteDao().getSpecificNote(noteId)
                    colorView.setBackgroundColor(Color.parseColor(notes.color))
                    etNoteTitle.setText(notes.title)
                    etNoteSubTitle.setText(notes.subTitle)
                    etNoteDesc.setText(notes.noteText)
                    if (notes.imgPath != ""){
                        selectedImagePath = notes.imgPath!!
                        imgNote.setImageBitmap(BitmapFactory.decodeFile(notes.imgPath))
                        layoutImage.visibility = View.VISIBLE
                        imgNote.visibility = View.VISIBLE
                        imgDelete.visibility = View.VISIBLE
                    }else{
                        layoutImage.visibility = View.GONE
                        imgNote.visibility = View.GONE
                        imgDelete.visibility = View.GONE
                    }

                    if (notes.webLink != ""){
                        webLink = notes.webLink!!
                        tvWebLink.text = notes.webLink
                        layoutWebUrl.visibility = View.VISIBLE
                        etWebLink.setText(notes.webLink)
                        imgUrlDelete.visibility = View.VISIBLE
                    }else{
                        imgUrlDelete.visibility = View.GONE
                        layoutWebUrl.visibility = View.GONE
                    }
                }
            }
        }
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                BroadcastReceiver, IntentFilter("bottom_sheet_action")
        )

        val sdf = SimpleDateFormat("dd/M/yyyy hh:mm:ss")

        currentDate = sdf.format(Date())
        colorView.setBackgroundColor(Color.parseColor(selectedColor))

        tvDateTime.text = currentDate

        imgDone.setOnClickListener {
            if (noteId != -1){
                updateNote()
            }else{
                saveNote()
            }
        }

        imgBack.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        imgMore.setOnClickListener{


            var noteBottomSheetFragment = NoteBottomSheetFragment.newInstance(noteId)
            noteBottomSheetFragment.show(requireActivity().supportFragmentManager,"Note Bottom Sheet Fragment")
        }

        imgDelete.setOnClickListener {
            selectedImagePath = ""
            layoutImage.visibility = View.GONE

        }

        btnOk.setOnClickListener {
            if (etWebLink.text.toString().trim().isNotEmpty()){
                checkWebUrl()
            }else{
                Toast.makeText(requireContext(),"Url is Required",Toast.LENGTH_SHORT).show()
            }
        }

        btnCancel.setOnClickListener {
            if (noteId != -1){
                tvWebLink.visibility = View.VISIBLE
                layoutWebUrl.visibility = View.GONE
            }else{
                layoutWebUrl.visibility = View.GONE
            }

        }

        imgUrlDelete.setOnClickListener {
            webLink = ""
            tvWebLink.visibility = View.GONE
            imgUrlDelete.visibility = View.GONE
            layoutWebUrl.visibility = View.GONE
        }

        tvWebLink.setOnClickListener {
            var intent = Intent(Intent.ACTION_VIEW,Uri.parse(etWebLink.text.toString()))
            startActivity(intent)
        }

    }


    private fun shareNote() {
        imgShare.setOnClickListener {
            if(!selectedImagePath.isEmpty()) {
                val bmpUri = Uri.parse(selectedImagePath)
                val shareIntent = Intent(android.content.Intent.ACTION_SEND);
                shareIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                shareIntent.putExtra(Intent.EXTRA_STREAM, bmpUri);
                shareIntent.putExtra(Intent.EXTRA_TEXT,etNoteDesc.text.toString())
                shareIntent.setType("image/png");
                startActivity(Intent.createChooser(shareIntent,"Share with"));
            } else {
                val shareIntent = Intent(android.content.Intent.ACTION_SEND)
                shareIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                shareIntent.putExtra(Intent.EXTRA_TEXT,etNoteDesc.text.toString())
                shareIntent.setType("text/plain");
                startActivity(Intent.createChooser(shareIntent,"Share with"));
            }

        }
    }


    private fun updateNote(){
        launch {

            context?.let {
                var notes = NotesDatabase.getDatabase(it).noteDao().getSpecificNote(noteId)

                notes.title = etNoteTitle.text.toString()
                notes.subTitle = etNoteSubTitle.text.toString()
                notes.noteText = etNoteDesc.text.toString()
                notes.dateTime = currentDate
                notes.color = selectedColor
                notes.imgPath = selectedImagePath
                notes.webLink = webLink

                NotesDatabase.getDatabase(it).noteDao().updateNote(notes)
                etNoteTitle.setText("")
                etNoteSubTitle.setText("")
                etNoteDesc.setText("")
                layoutImage.visibility = View.GONE
                imgNote.visibility = View.GONE
                tvWebLink.visibility = View.GONE
                requireActivity().supportFragmentManager.popBackStack()
            }
        }
    }
    private fun saveNote(){

        if (etNoteTitle.text.isNullOrEmpty()){
            Toast.makeText(context,"Note Title is Required",Toast.LENGTH_SHORT).show()
        }
        else if (etNoteSubTitle.text.isNullOrEmpty()){

            Toast.makeText(context,"Note Sub Title is Required",Toast.LENGTH_SHORT).show()
        }

        else if (etNoteDesc.text.isNullOrEmpty()){

            Toast.makeText(context,"Note Description is Required",Toast.LENGTH_SHORT).show()
        }

        else{

            launch {
                var notes = Notes()
                notes.title = etNoteTitle.text.toString()
                notes.subTitle = etNoteSubTitle.text.toString()
                notes.noteText = etNoteDesc.text.toString()
                notes.dateTime = currentDate
                notes.color = selectedColor
                notes.imgPath = selectedImagePath
                notes.webLink = webLink
                context?.let {
                    NotesDatabase.getDatabase(it).noteDao().insertNotes(notes)
                    etNoteTitle.setText("")
                    etNoteSubTitle.setText("")
                    etNoteDesc.setText("")
                    layoutImage.visibility = View.GONE
                    imgNote.visibility = View.GONE
                    tvWebLink.visibility = View.GONE
                    requireActivity().supportFragmentManager.popBackStack()
                }
            }
        }

    }

    private fun deleteNote(){

        launch {
            context?.let {
                NotesDatabase.getDatabase(it).noteDao().deleteSpecificNote(noteId)
                requireActivity().supportFragmentManager.popBackStack()
            }
        }
    }

    private fun checkWebUrl(){
        if (Patterns.WEB_URL.matcher(etWebLink.text.toString()).matches()){
            layoutWebUrl.visibility = View.GONE
            etWebLink.isEnabled = false
            webLink = etWebLink.text.toString()
            tvWebLink.visibility = View.VISIBLE
            tvWebLink.text = etWebLink.text.toString()
        }else{
            Toast.makeText(requireContext(),"Url is not valid",Toast.LENGTH_SHORT).show()
        }
    }


    private val BroadcastReceiver : BroadcastReceiver = object :BroadcastReceiver(){
        override fun onReceive(p0: Context?, p1: Intent?) {

            var actionColor = p1!!.getStringExtra("action")

            when(actionColor!!){

                "Blue" -> {
                    selectedColor = p1.getStringExtra("selectedColor")!!
                    colorView.setBackgroundColor(Color.parseColor(selectedColor))

                }

                "Yellow" -> {
                    selectedColor = p1.getStringExtra("selectedColor")!!
                    colorView.setBackgroundColor(Color.parseColor(selectedColor))

                }


                "Purple" -> {
                    selectedColor = p1.getStringExtra("selectedColor")!!
                    colorView.setBackgroundColor(Color.parseColor(selectedColor))

                }


                "Green" -> {
                    selectedColor = p1.getStringExtra("selectedColor")!!
                    colorView.setBackgroundColor(Color.parseColor(selectedColor))

                }


                "Orange" -> {
                    selectedColor = p1.getStringExtra("selectedColor")!!
                    colorView.setBackgroundColor(Color.parseColor(selectedColor))

                }


                "Black" -> {
                    selectedColor = p1.getStringExtra("selectedColor")!!
                    colorView.setBackgroundColor(Color.parseColor(selectedColor))

                }
                "Speech" ->{
                    speakOut()
                    layoutWebUrl.visibility = View.GONE
                }

                "Mic"->{
                    SpeechToText()
                    layoutWebUrl.visibility = View.GONE
                }

                "Scan" ->{
                    scanImagetoText()
                    layoutWebUrl.visibility = View.GONE
                }

                "Image" ->{
                    readStorageTask()
                    layoutWebUrl.visibility = View.GONE
                }

                "WebUrl" ->{
                    layoutWebUrl.visibility = View.VISIBLE
                }
                "DeleteNote" -> {
                    //delete note
                    deleteNote()
                }


                else -> {
                    layoutImage.visibility = View.GONE
                    imgNote.visibility = View.GONE
                    layoutWebUrl.visibility = View.GONE
                    selectedColor = p1.getStringExtra("selectedColor")!!
                    colorView.setBackgroundColor(Color.parseColor(selectedColor))

                }
            }
        }

    }

    private fun scanImagetoText() {
        if(checkPermissions()){
            captureImage()
        }
        else{
            requestPermission()
        }

    }

    private fun checkPermissions(): Boolean {
        var cameraPermission:Int=
            ContextCompat.checkSelfPermission(requireActivity().applicationContext, Manifest.permission.CAMERA)
        return cameraPermission== PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission(){
        var PERMISSION_CODE:Int=200
        var arrayString:Array<String> = arrayOf(Manifest.permission.CAMERA)
        ActivityCompat.requestPermissions(requireActivity(), arrayString,PERMISSION_CODE)
    }
    private  fun captureImage():Unit{
        var takePicture:Intent=Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if(takePicture.resolveActivity(requireActivity().packageManager)!=null){
            startActivityForResult(takePicture,REQUEST_IMAGE_CAPTURE)
        }
    }


    private fun detectText():Unit{
        var image: InputImage = InputImage.fromBitmap(imageBitmap,0)
        var recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        var result: Task<Text> =recognizer.process(image).addOnSuccessListener {
            var result: StringBuilder = StringBuilder()
            var block: Text.TextBlock
            for(block in it.getTextBlocks()){
                var blockText:String=block.text
                val blockCornerPoint = block.cornerPoints
                val blockFrame =block.boundingBox
                for(line in block.lines){
                    val lineText=line.text
                    val lineCornerPoints = line.cornerPoints
                    val lineRect = line.boundingBox
                    for(element in line.elements){
                        val elementText = element.text
                        result.append(elementText)
                    }
                    etNoteDesc.setText(blockText)
                }
            }

        }.addOnFailureListener(){
            Toast.makeText(requireActivity(),"Fail to detect text from image",Toast.LENGTH_SHORT).show()

        }
    }

    override fun onDestroy() {

        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(BroadcastReceiver)

        if (tts != null) {
            tts!!.stop()
            tts!!.shutdown()
        }
        super.onDestroy()


    }

    private fun hasReadStoragePerm():Boolean{
        return EasyPermissions.hasPermissions(requireContext(),Manifest.permission.READ_EXTERNAL_STORAGE)
    }


    private fun readStorageTask(){
        if (hasReadStoragePerm()){


            pickImageFromGallery()
        }else{
            EasyPermissions.requestPermissions(
                    requireActivity(),
                    getString(R.string.storage_permission_text),
                    READ_STORAGE_PERM,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    private fun pickImageFromGallery(){
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        if (intent.resolveActivity(requireActivity().packageManager) != null){
            startActivityForResult(intent,REQUEST_CODE_IMAGE)
        }
    }

    private fun getPathFromUri(contentUri: Uri): String? {
        var filePath:String? = null
        val cursor = requireActivity().contentResolver.query(contentUri,null,null,null,null)
        if (cursor == null){
            filePath = contentUri.path
        }else{
            cursor.moveToFirst()
            var index = cursor.getColumnIndex("_data")
            filePath = cursor.getString(index)
            cursor.close()
        }
        return filePath
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode==REQUEST_IMAGE_CAPTURE && resultCode== RESULT_OK){
            var extras: Bundle? = data?.extras
            if (extras != null) {
                imageBitmap= extras.get("data") as Bitmap
                detectText()
            }
        }
        if (requestCode == REQUEST_CODE_IMAGE && resultCode == RESULT_OK){
            if (data != null){
                var selectedImageUrl = data.data
                if (selectedImageUrl != null){
                    try {
                        var inputStream = requireActivity().contentResolver.openInputStream(selectedImageUrl)
                        var bitmap = BitmapFactory.decodeStream(inputStream)
                        imgNote.setImageBitmap(bitmap)
                        imgNote.visibility = View.VISIBLE
                        layoutImage.visibility = View.VISIBLE

                        selectedImagePath = getPathFromUri(selectedImageUrl)!!
                    }catch (e:Exception){
                        Toast.makeText(requireContext(),e.message,Toast.LENGTH_SHORT).show()
                    }

                }
            }
        }

//
                if (requestCode== REQUEST_CODE_STT && resultCode == Activity.RESULT_OK && data != null) {
                    val result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    result?.let {
                        val recognizedText = it[0]
                        etNoteDesc.setText(recognizedText)
                    }
                }


    }



    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        EasyPermissions.onRequestPermissionsResult(requestCode,permissions,grantResults,requireActivity())

        if(grantResults.size>0){
            var cameraPermission:Boolean=grantResults[0]== PackageManager.PERMISSION_GRANTED
            if(cameraPermission){
                Toast.makeText(requireActivity(),"Permissions Granted..",Toast.LENGTH_SHORT).show()
                captureImage()
            }
            else{
                Toast.makeText(requireActivity(),"Permissions denied..",Toast.LENGTH_SHORT).show()

            }
        }
    }


    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(requireActivity(),perms)){
            AppSettingsDialog.Builder(requireActivity()).build().show()
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {

    }

    override fun onRationaleDenied(requestCode: Int) {

    }

    override fun onRationaleAccepted(requestCode: Int) {

    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // set US English as language for tts
            val result = tts!!.setLanguage(Locale.US)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS","The Language specified is not supported!")
            }

        } else {
            Log.e("TTS", "Initilization Failed!")
        }

    }
    private fun speakOut() {
        val text = etNoteDesc!!.text.toString()
        tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null,"")
    }
    private fun SpeechToText(){
        val sttIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        sttIntent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        sttIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        sttIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now!")

        try {
            startActivityForResult(sttIntent, REQUEST_CODE_STT)
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
            Toast.makeText(requireActivity(), "Your device does not support STT.", Toast.LENGTH_LONG).show()
        }
    }

    }

