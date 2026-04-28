package com.templ.templ

import com.intellij.lang.Commenter

class TemplCommenter : Commenter {
    override fun getLineCommentPrefix(): String? = null

    override fun getBlockCommentPrefix(): String = "<!--"

    override fun getBlockCommentSuffix(): String = "-->"

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null
}
