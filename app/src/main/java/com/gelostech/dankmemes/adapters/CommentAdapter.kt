package com.gelostech.dankmemes.adapters

import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import com.gelostech.dankmemes.R
import com.gelostech.dankmemes.commoners.AppUtils
import com.gelostech.dankmemes.models.CommentModel
import com.gelostech.dankmemes.utils.TimeFormatter
import com.gelostech.dankmemes.utils.inflate
import com.gelostech.dankmemes.utils.loadUrl
import com.google.firebase.storage.FirebaseStorage
import kotlinx.android.synthetic.main.item_comment.view.*
import java.lang.ref.WeakReference

class CommentAdapter(onItemClickListener: OnItemClickListener): RecyclerView.Adapter<CommentAdapter.CommentHolder>() {
    private val comments = mutableListOf<CommentModel>()
    private val onItemClickListener = onItemClickListener;

    fun addComment(comment: CommentModel) {
        comments.add(comment)
        notifyItemInserted(comments.size - 1)
    }

    fun removeComment(comment: CommentModel) {
        var indexToRemove: Int = -1

        for ((index, commentModel) in comments.withIndex()) {
            if (comment.commentKey == commentModel.commentKey) {
                indexToRemove = index
            }
        }

        comments.removeAt(indexToRemove)
        notifyItemRemoved(indexToRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentHolder {
        return CommentHolder(parent.inflate(R.layout.item_comment), onItemClickListener)
    }

    override fun getItemCount(): Int = comments.size

    override fun onBindViewHolder(holder: CommentHolder, position: Int) {
        holder.bindView(comments[position])
    }

    class CommentHolder(itemView: View, onItemClickListener: OnItemClickListener) : RecyclerView.ViewHolder(itemView), View.OnClickListener, View.OnLongClickListener {
        private val commentRoot = itemView.commentRoot
        private val commentIcon = itemView.commentIcon
        private val commentUser = itemView.commentUser
        private val commentText = itemView.commentText
        private val commentTime = itemView.commentTime
        private val weakReference = WeakReference<OnItemClickListener>(onItemClickListener)
        private lateinit var commentObject: CommentModel

        init {
            commentIcon.setOnClickListener(this)
            commentRoot.setOnLongClickListener(this)
        }

        fun bindView(commentObject: CommentModel) {
            this.commentObject = commentObject

            with(commentObject) {
                //loadIcon(commentObject)
                if (userAvatar.isNullOrEmpty()) {
                    loadIcon(commentObject)
                } else {
                    commentIcon.loadUrl(userAvatar!!)
                }
                commentUser.text = userName
                commentText.text = comment
                commentTime.text = TimeFormatter().getTimeStamp(timeStamp!!)
            }
        }

        private fun loadIcon(comment: CommentModel) {
            if (AppUtils.getBitmap(comment.authorId!!) == null)
                commentIcon.loadUrl(R.drawable.person)
            else
                commentIcon.setImageBitmap(AppUtils.getBitmap(comment.authorId!!))

            val avatarRef = FirebaseStorage.getInstance().reference.child("avatars").child(comment.authorId!!)
            avatarRef.downloadUrl.addOnSuccessListener {
                AppUtils.cacheBitmap(it.toString(), comment.authorId!!)
                commentIcon.loadUrl(it.toString())
            }
        }

        override fun onClick(v: View?) {
            when(v?.id) {
                commentIcon.id -> weakReference.get()!!.onItemClick(commentObject, 0)
            }
        }

        override fun onLongClick(v: View?): Boolean {
            when(v?.id) {
                commentRoot.id -> weakReference.get()!!.onLongItemClick(commentObject)
            }

            return true
        }
    }

    interface OnItemClickListener{
        fun onItemClick(comment: CommentModel, viewId: Int)
        fun onLongItemClick(comment: CommentModel)
    }

}