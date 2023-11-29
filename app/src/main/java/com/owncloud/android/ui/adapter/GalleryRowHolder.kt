/*
 *
 * Nextcloud Android client application
 *
 * @author Tobias Kaminsky
 * Copyright (C) 2022 Tobias Kaminsky
 * Copyright (C) 2022 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.owncloud.android.ui.adapter

import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.get
import com.afollestad.sectionedrecyclerview.SectionedViewHolder
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.nextcloud.client.account.User
import com.nextcloud.client.network.ClientFactory
import com.owncloud.android.R
import com.owncloud.android.databinding.GalleryRowBinding
import com.owncloud.android.datamodel.GalleryRow
import com.owncloud.android.datamodel.OCFile
import com.owncloud.android.lib.common.OwnCloudClientManagerFactory
import com.owncloud.android.lib.resources.files.model.ImageDimension
import com.owncloud.android.utils.DisplayUtils
import com.owncloud.android.utils.MimeTypeUtil
import com.owncloud.android.utils.glide.CustomGlideStreamLoader
import com.owncloud.android.utils.theme.ViewThemeUtils
import java.net.URLEncoder

@Suppress("LongParameterList")
class GalleryRowHolder(
    val binding: GalleryRowBinding,
    private val viewThemeUtils: ViewThemeUtils,
    private val defaultThumbnailSize: Float,
    private val galleryAdapter: GalleryAdapter,
    private val user: User,
    private val clientFactory: ClientFactory,
    private val galleryRowItemClick: GalleryRowItemClick
) : SectionedViewHolder(binding.root) {

    private val context = galleryAdapter.context
    private lateinit var currentRow: GalleryRow

    private val client = OwnCloudClientManagerFactory.getDefaultSingleton()

    @Suppress("DEPRECATION")
    private val baseUri = client.getClientFor(user.toOwnCloudAccount(), context).baseUri
    private val previewLink = "/index.php/core/preview.png?file="
    private val imageDownloadWidth = "&x=$defaultThumbnailSize"
    private val imageDownloadHeight = "&y=$defaultThumbnailSize"
    private val mode = "&a=1&mode=cover&forceIcon=0"
    private val screenWidth =
        DisplayUtils.convertDpToPixel(context.resources.configuration.screenWidthDp.toFloat(), context)
            .toFloat()
    private val defaultImageDimension = ImageDimension(defaultThumbnailSize, defaultThumbnailSize)

    interface GalleryRowItemClick {
        fun openMedia(file: OCFile)
    }

    fun bind(row: GalleryRow) {
        currentRow = row

        row.files.forEach { file ->
            addImage(file)
        }

        if (binding.rowLayout.childCount > row.files.size) {
            binding.rowLayout.removeViewsInLayout(row.files.size - 1, (binding.rowLayout.childCount - row.files.size))
        }

        val shrinkRatio = computeShrinkRatio(row)

        row.files.forEachIndexed { index, file ->
            adjustImage(row, index, file, shrinkRatio)
        }
    }

    private fun addImage(file: OCFile) {
        val thumbnail = ImageView(context)

        val imageUrl: String =
            baseUri.toString() +
                previewLink +
                URLEncoder.encode(file.remotePath, Charsets.UTF_8.name()) +
                imageDownloadWidth +
                imageDownloadHeight +
                mode

        val placeholder = MimeTypeUtil.getFileTypeIcon(
            file.mimeType,
            file.fileName,
            context,
            viewThemeUtils
        )

        Glide
            .with(context)
            .using(CustomGlideStreamLoader(user, clientFactory))
            .load(imageUrl)
            .asBitmap()
            .placeholder(placeholder)
            .fitCenter()
            .diskCacheStrategy(DiskCacheStrategy.SOURCE)
            .dontAnimate()
            .into(thumbnail)

        val layout = LinearLayout(context).apply {
            addView(thumbnail)
            setOnClickListener {
                galleryRowItemClick.openMedia(file)
            }
        }

        binding.rowLayout.addView(layout)
    }

    private fun adjustImage(row: GalleryRow, index: Int, file: OCFile, shrinkRatio: Float) {
        val imageDimension = file.imageDimension ?: ImageDimension(defaultThumbnailSize, defaultThumbnailSize)
        val height = (imageDimension.height * shrinkRatio).toInt()
        val width = (imageDimension.width * shrinkRatio).toInt()

        val linearLayout = binding.rowLayout[index] as LinearLayout
        val thumbnail = linearLayout[0] as ImageView


        thumbnail.run {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            Log.d("","FILE: " + file.remotePath)
            Log.d("","FILEWIDTH: " + width)
            Log.d("","FILEHEIGHT: " + width)
            layoutParams = LinearLayout.LayoutParams(width, height).apply {
                val zero = context.resources.getInteger(R.integer.zero)
                val margin = context.resources.getInteger(R.integer.small_margin)
                if (index < (row.files.size - 1)) {
                    setMargins(zero, zero, margin, margin)
                } else {
                    setMargins(zero, zero, zero, margin)
                }
            }
        }
    }

    fun redraw() {
        bind(currentRow)
    }

    @SuppressWarnings("MagicNumber", "ComplexMethod")
    private fun computeShrinkRatio(row: GalleryRow): Float {
        if (row.files.size > 1) {
            var summedWidth = 0f

            row.files.forEach { file ->
                val (width, height) = file.imageDimension ?: defaultImageDimension

                val scaleFactor = row.getMaxHeight() / height

                val scaledWidth = width * scaleFactor
                val scaledHeight = height * scaleFactor

                file.imageDimension = ImageDimension(scaledWidth, scaledHeight)

                summedWidth += scaledWidth
            }

            val sizeToFactorMap = mapOf(
                2 to 5 / 2f,
                3 to 4 / 3f,
                4 to 4 / 5f,
                5 to 1f
            )

            var c = 1f

            // this ensures that files in last row are better visible,
            // e.g. when 2 images are there, it uses 2/5 of screen
            if (galleryAdapter.columns == 5) {
                c = sizeToFactorMap[row.files.size] ?: c
            }

            return (screenWidth / c) / summedWidth
        } else {
            val firstFileImageDimension = row.files[0].imageDimension ?: defaultImageDimension
            return (screenWidth / galleryAdapter.columns) / firstFileImageDimension.width
        }
    }
}
