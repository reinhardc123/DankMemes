package com.gelostech.dankmemes.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import com.cocosw.bottomsheet.BottomSheet
import com.gelostech.dankmemes.R
import com.gelostech.dankmemes.adapters.ProfileMemesAdapter
import com.gelostech.dankmemes.commoners.BaseActivity
import com.gelostech.dankmemes.commoners.Config
import com.gelostech.dankmemes.commoners.DankMemesUtil
import com.gelostech.dankmemes.models.FaveModel
import com.gelostech.dankmemes.models.MemeModel
import com.gelostech.dankmemes.models.ReportModel
import com.gelostech.dankmemes.models.UserModel
import com.gelostech.dankmemes.utils.RecyclerFormatter
import com.google.firebase.database.*
import de.hdodenhof.circleimageview.CircleImageView
import org.jetbrains.anko.alert
import org.jetbrains.anko.toast

class ProfileActivity : BaseActivity(), ProfileMemesAdapter.OnItemClickListener, ProfileMemesAdapter.OnProfileClickListener {
    private lateinit var memesAdapter: ProfileMemesAdapter
    private lateinit var image: Bitmap
    private lateinit var profileRef: DatabaseReference
    private lateinit var memesQuery: Query
    private lateinit var bs: BottomSheet.Builder
    private lateinit var name: String

    companion object {
        private var TAG = ProfileActivity::class.java.simpleName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val userId = intent.getStringExtra("userId")

        initViews()

        profileRef = getDatabaseReference().child("users").child(userId)
        memesQuery = getDatabaseReference().child("dank-memes").orderByChild("memePosterID").equalTo(userId)

        profileRef.addValueEventListener(profileListener)
        memesQuery.addValueEventListener(memesValueListener)
        memesQuery.addChildEventListener(memesChildListener)
    }

    private fun initViews() {
        setSupportActionBar(viewProfileToolbar)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.profile)

        viewProfileRv.setHasFixedSize(true)
        viewProfileRv.layoutManager = LinearLayoutManager(this)
        viewProfileRv.addItemDecoration(RecyclerFormatter.DoubleDividerItemDecoration(this))
        viewProfileRv.itemAnimator = DefaultItemAnimator()

        memesAdapter = ProfileMemesAdapter(this, this, this)
        viewProfileRv.adapter = memesAdapter

    }

    private val profileListener = object : ValueEventListener {
        override fun onCancelled(p0: DatabaseError) {
            Log.e(TAG, "Error loading profile: ${p0.message}")
        }

        override fun onDataChange(p0: DataSnapshot) {
            val user = p0.getValue(UserModel::class.java)!!
            name = user.userName!!

            memesAdapter.addProfile(user)
        }
    }

    private val memesValueListener = object : ValueEventListener {
        override fun onCancelled(p0: DatabaseError) {
            Log.e(TAG, "Error loading memes: ${p0.message}")
        }

        override fun onDataChange(p0: DataSnapshot) {
            if (p0.exists()) {
                viewProfileEmptyState.visibility = View.GONE
                viewProfileRv.visibility = View.VISIBLE
            } else {
                viewProfileEmptyStateText.text = "$name hasn't posted any memes yet"
                viewProfileRv.visibility = View.GONE
                viewProfileEmptyState.visibility = View.VISIBLE
            }
        }
    }

    private val memesChildListener = object : ChildEventListener {
        override fun onCancelled(p0: DatabaseError) {
            Log.e(TAG, "Error loading memes: ${p0.message}")
        }

        override fun onChildMoved(p0: DataSnapshot, p1: String?) {
            Log.e(TAG, "Meme moved: ${p0.key}")
        }

        override fun onChildChanged(p0: DataSnapshot, p1: String?) {
            val meme = p0.getValue(MemeModel::class.java)
            memesAdapter.updateMeme(meme!!)
        }

        override fun onChildAdded(p0: DataSnapshot, p1: String?) {
            val meme = p0.getValue(MemeModel::class.java)
            memesAdapter.addMeme(meme!!)
        }

        override fun onChildRemoved(p0: DataSnapshot) {
            val meme = p0.getValue(MemeModel::class.java)
            memesAdapter.removeMeme(meme!!)
        }
    }

    /**
     *  Handle meme item clicks
     *
     *  0 -> like meme
     *  1 -> more options
     *  2 -> fave meme
     *  3 -> comments
     *  4 -> image
     *  5 -> user icon
     */
    override fun onItemClick(meme: MemeModel, viewID: Int, image: Bitmap?) {
        when(viewID) {
            0 -> likePost(meme.id!!)
            1 -> showBottomSheet(meme, image!!)
            2 -> favePost(meme.id!!)
            3 -> showComments(meme)
            4 -> showMeme(meme, image!!)

        }
    }

    override fun onProfileClick(user: UserModel, view: CircleImageView) {
        image = (view.drawable as BitmapDrawable).bitmap
        DankMemesUtil.saveTemporaryImage(this, image)

        val i = Intent(this, ViewMemeActivity::class.java)
        i.putExtra(Config.PIC_URL, user.userAvatar)
        DankMemesUtil.fadeIn(this)
    }

    private fun showMeme(meme: MemeModel, image: Bitmap) {
        val i = Intent(this, ViewMemeActivity::class.java)
        DankMemesUtil.saveTemporaryImage(this, image)
        i.putExtra("caption", meme.caption)
        i.putExtra(Config.PIC_URL, meme.imageUrl)
        startActivity(i)
        DankMemesUtil.fadeIn(this)
    }

    private fun showBottomSheet(meme: MemeModel, image: Bitmap) {
        bs = BottomSheet.Builder(this).sheet(R.menu.main_bottomsheet)

        bs.listener { _, which ->

            when(which) {
                R.id.bs_share -> DankMemesUtil.shareImage(this, image)
                R.id.bs_save -> {
                    if (storagePermissionGranted()) {
                        DankMemesUtil.saveImage(this, image)
                    } else requestStoragePermission()
                }
                R.id.bs_report -> showReportDialog(meme)
            }

        }.show()

    }

    private fun showComments(meme: MemeModel) {
        val i = Intent(this, CommentActivity::class.java)
        i.putExtra("memeId", meme.id)
        startActivity(i)
        overridePendingTransition(R.anim.enter_b, R.anim.exit_a)
    }

    private fun likePost(id: String) {
        getDatabaseReference().child("dank-memes").child(id).runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val meme = mutableData.getValue<MemeModel>(MemeModel::class.java)

                if (meme!!.likes.containsKey(getUid())) {
                    meme.likesCount = meme.likesCount!! - 1
                    meme.likes.remove(getUid())

                } else  {
                    meme.likesCount = meme.likesCount!! + 1
                    meme.likes[getUid()] = true
                }

                mutableData.value = meme
                return Transaction.success(mutableData)
            }

            override fun onComplete(databaseError: DatabaseError?, b: Boolean, dataSnapshot: DataSnapshot?) {

                Log.d(javaClass.simpleName, "postTransaction:onComplete: $databaseError")
            }
        })
    }

    private fun favePost(id: String) {
        getDatabaseReference().child("dank-memes").child(id).runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val meme = mutableData.getValue<MemeModel>(MemeModel::class.java)

                if (meme!!.faves.containsKey(getUid())) {
                    meme.faves.remove(getUid())

                    getDatabaseReference().child("favorites").child(getUid()).child(meme.id!!).removeValue()

                } else  {
                    meme.faves[getUid()] = true

                    val fave = FaveModel()
                    fave.faveKey = meme.id!!
                    fave.commentId = meme.id!!
                    fave.picUrl = meme.imageUrl!!

                    getDatabaseReference().child("favorites").child(getUid()).child(meme.id!!).setValue(fave)
                }

                mutableData.value = meme
                return Transaction.success(mutableData)
            }

            override fun onComplete(databaseError: DatabaseError?, b: Boolean, dataSnapshot: DataSnapshot?) {

                Log.d(javaClass.simpleName, "postTransaction:onComplete: $databaseError")
            }
        })
    }

    private fun showReportDialog(meme: MemeModel) {
        val editText = EditText(this)
        val layout = FrameLayout(this)
        layout.setPaddingRelative(45,15,45,0)
        layout.addView(editText)

        alert("Please provide a reason for reporting") {
            customView = layout

            positiveButton("REPORT") {
                if (!DankMemesUtil.validated(editText)) {
                    toast("Please enter a reason to report")
                    return@positiveButton
                }

                val key = getDatabaseReference().child("reports").push().key
                val reason = editText.text.toString().trim()

                val report = ReportModel()
                report.id = key
                report.memeId = meme.id
                report.memePosterId = meme.memePosterID
                report.reporterId = getUid()
                report.memeUrl = meme.imageUrl
                report.reason = reason
                report.time = System.currentTimeMillis()

                getDatabaseReference().child("reports").child(key!!).setValue(report).addOnCompleteListener {
                    toast("Meme reported!")
                }

            }

            negativeButton("CANCEL"){}
        }.show()
    }


    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when(item?.itemId) {
            android.R.id.home -> onBackPressed()
        }

        return true
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.enter_a, R.anim.exit_b)
    }

    override fun onDestroy() {
        profileRef.removeEventListener(profileListener)
        memesQuery.removeEventListener(memesValueListener)
        memesQuery.removeEventListener(memesChildListener)
        super.onDestroy()
    }
}
