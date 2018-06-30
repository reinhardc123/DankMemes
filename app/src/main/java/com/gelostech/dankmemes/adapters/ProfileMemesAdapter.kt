package com.gelostech.dankmemes.adapters

import android.content.Context
import android.graphics.Bitmap
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import com.gelostech.dankmemes.R
import com.gelostech.dankmemes.commoners.DankMemesUtil.cacheBitmap
import com.gelostech.dankmemes.commoners.DankMemesUtil.getBitmap
import com.gelostech.dankmemes.commoners.DankMemesUtil.setDrawable
import com.gelostech.dankmemes.commoners.MyBounceInterpolator
import com.gelostech.dankmemes.models.MemeModel
import com.gelostech.dankmemes.models.UserModel
import com.gelostech.dankmemes.utils.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.makeramen.roundedimageview.RoundedDrawable
import com.mikepenz.fontawesome_typeface_library.FontAwesome
import com.mikepenz.ionicons_typeface_library.Ionicons
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.android.synthetic.main.item_header.view.*
import kotlinx.android.synthetic.main.item_meme.view.*
import java.lang.ref.WeakReference

class ProfileMemesAdapter(private val context: Context, private val onItemClickListener: OnItemClickListener,
                          private val onProfileClickListener: OnProfileClickListener) : RecyclerView.Adapter<RecyclerView.ViewHolder>(){

    private val memes = mutableListOf<MemeModel>()
    private var user = UserModel()

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_MEME = 1
    }

    fun addProfile(user: UserModel) {
        this.user = user
    }

    fun addMeme(meme: MemeModel) {
        memes.add(0, meme)
        notifyItemInserted(0)
    }

    fun updateMeme(meme: MemeModel) {
        for ((index, memeModel) in memes.withIndex()) {
            if (meme.id == memeModel.id) {
                memes[index] = meme
                notifyItemChanged(index, meme)
            }
        }
    }

    fun removeMeme(meme: MemeModel) {
        var indexToRemove: Int = -1

        for ((index, memeModel) in memes.withIndex()) {
            if (meme.id == memeModel.id) {
                indexToRemove = index
            }
        }

        memes.removeAt(indexToRemove)
        notifyItemRemoved(indexToRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        return when(viewType) {
            TYPE_HEADER -> MemeHeader(parent.inflate(R.layout.item_header), onProfileClickListener)
            TYPE_MEME -> MemeHolder(parent.inflate(R.layout.item_meme), onItemClickListener, context)

            else -> MemeHolder(parent.inflate(R.layout.item_meme), onItemClickListener, context)
        }

    }

    override fun getItemCount(): Int = memes.size + 1

    override fun getItemViewType(position: Int): Int {
        return when(position) {
            0 -> TYPE_HEADER
            else -> TYPE_MEME
        }
    }

    private fun getMeme(position: Int): MemeModel {
        return memes[position - 1]
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        if (holder is MemeHeader) {
            holder.bindProfile(user)
        } else if (holder is MemeHolder) {
            holder.bindViews(getMeme(position))
        }
    }

    class MemeHeader(view:View, onProfileClickListener: OnProfileClickListener) : RecyclerView.ViewHolder(view), View.OnClickListener {

        private var weakReference: WeakReference<OnProfileClickListener> = WeakReference(onProfileClickListener)
        private lateinit var user: UserModel

        private val profilePic = view.profileImage
        private val profileName = view.profileName
        private val profileBio = view.profileBio


        init {
            profilePic.setOnClickListener(this)
        }

        fun bindProfile(user: UserModel) {
            this.user = user

            with(user) {
                userAvatar?.let { profilePic.loadUrl(it) }
                profileName.text = userName
                profileBio.text = userBio
            }
        }

        override fun onClick(v: View?) {

            when(v?.id) {
                profilePic.id -> weakReference.get()!!.onProfileClick(user, profilePic)
            }

        }
    }

    class MemeHolder(itemView: View, onItemClickListener: OnItemClickListener, context: Context) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        private val userIcon = itemView.memeIcon
        private val userName = itemView.memeUser
        private val memeTime = itemView.memeTime
        private val memeMore = itemView.memeMore
        private val memeCaption = itemView.memeCaption
        private val memeImage = itemView.memeImage
        private val memeLike = itemView.memeLike
        private val memeComment = itemView.memeComment
        private val memeFave = itemView.memeFave
        private var anim: Animation
        private var bounceInterpolator: MyBounceInterpolator
        private var weakReference: WeakReference<OnItemClickListener> = WeakReference(onItemClickListener)
        private lateinit var meme: MemeModel
        private lateinit var image: Bitmap
        private var TAG = MemeHolder::class.java.simpleName
        private val c = context

        init {
            memeMore.setImageDrawable(setDrawable(context, Ionicons.Icon.ion_android_more_vertical, R.color.secondaryText, 14))
            memeComment.setDrawable(setDrawable(context, Ionicons.Icon.ion_ios_chatboxes_outline, R.color.secondaryText, 20))

            anim = AnimationUtils.loadAnimation(context, R.anim.bounce)
            bounceInterpolator = MyBounceInterpolator(0.2, 20.0)
            anim.interpolator = bounceInterpolator

            memeMore.setOnClickListener(this)
            memeFave.setOnClickListener(this)
            memeComment.setOnClickListener(this)
            memeLike.setOnClickListener(this)
            memeImage.setOnClickListener(this)
            userIcon.setOnClickListener(this)
        }

        fun bindViews(meme: MemeModel) {
            this.meme = meme

            with(meme) {
                loadIcon(meme)
                //loadFromStorage(memePosterID!!, userIcon)

                userName.text = memePoster
                memeTime.text = TimeFormatter().getTimeStamp(time!!)
                if (caption!!.isNullOrEmpty()) {
                    memeCaption.hideView()
                } else {
                    if (!memeCaption.isShown) memeCaption.showView()
                    memeCaption.text = caption
                }
                memeImage.loadUrl(imageUrl!!)
                memeLike.text = "$likesCount likes"
                comments(commentsCount!!)

                if (likes.containsKey(FirebaseAuth.getInstance().currentUser!!.uid))
                    liked(likesCount!!)
                else
                    notLiked(likesCount!!)

                if (!faves.containsKey(FirebaseAuth.getInstance().currentUser!!.uid))
                    memeFave.setImageDrawable(setDrawable(c, Ionicons.Icon.ion_ios_heart_outline, R.color.secondaryText, 19))
                else
                    memeFave.setImageDrawable(setDrawable(c, Ionicons.Icon.ion_ios_heart, R.color.pink, 19))
            }

        }

        private fun loadIcon(meme: MemeModel) {
            if (getBitmap(meme.memePosterID!!) == null)
                userIcon.loadUrl(R.drawable.person)
            else
                userIcon.setImageBitmap(getBitmap(meme.memePosterID!!))

            val avatarRef = FirebaseStorage.getInstance().reference.child("avatars").child(meme.memePosterID!!)
            avatarRef.downloadUrl.addOnSuccessListener {
                cacheBitmap(it.toString(), meme.memePosterID!!)
                userIcon.loadUrl(it.toString())
            }
        }

        private fun comments(comments: Int) {
            when {
                comments > 1 -> memeComment.text = "$comments comments"
                comments == 1 -> memeComment.text = "$comments comment"
                else -> memeComment.text = "comment"
            }
        }

        private fun liked(likes: Int) {
            memeLike.setDrawable(setDrawable(c, FontAwesome.Icon.faw_thumbs_up2, R.color.colorAccent, 20))
            when {
                likes > 1 -> memeLike.text = likes.toString() + " likes"
                likes == 1 -> memeLike.text = likes.toString() + " like"
                else -> memeLike.text = "like"
            }
            memeLike.setTextColor(ContextCompat.getColor(c, R.color.colorAccent))
        }

        private fun notLiked(likes: Int) {
            memeLike.setDrawable(setDrawable(c, FontAwesome.Icon.faw_thumbs_up, R.color.secondaryText, 20))
            when {
                likes > 1 -> memeLike.text = likes.toString() + " likes"
                likes == 1 -> memeLike.text = likes.toString() + " like"
                else -> memeLike.text = "like"
            }
            memeLike.setTextColor(ContextCompat.getColor(c, R.color.textGray))
        }

        override fun onClick(v: View?) {
            image = (memeImage.drawable as RoundedDrawable).sourceBitmap

            /**
             *  0 -> like meme
             *  1 -> more options
             *  2 -> fave meme
             *  3 -> comments
             *  4 -> image
             *  5 -> user icon
             */

            when(v!!.id) {
                memeLike.id -> {
                    weakReference.get()!!.onItemClick(meme, 0, null)
                    memeLike.startAnimation(anim)
                }

                memeMore.id -> weakReference.get()!!.onItemClick(meme, 1, image)
                memeFave.id -> weakReference.get()!!.onItemClick(meme, 2, null)
                memeComment.id -> weakReference.get()!!.onItemClick(meme, 3, null)
                memeImage.id -> weakReference.get()!!.onItemClick(meme, 4, image)
                userIcon.id -> weakReference.get()!!.onItemClick(meme, 5, null)
            }
        }

    }

    interface OnItemClickListener {
        fun onItemClick(meme: MemeModel, viewID: Int, image: Bitmap?)
    }

    interface OnProfileClickListener {
        fun onProfileClick(user: UserModel, view: CircleImageView)
    }

}