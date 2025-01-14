package com.lxj.statelayout

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import android.util.AttributeSet
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.lxj.statelayout.State.*

class StateLayout @JvmOverloads constructor(context: Context, attributeSet: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attributeSet, defStyleAttr) {
    var state = None // default state
    var loadingView: View? = null
    var emptyView: View? = null
    var errorView: View? = null
    var contentView: View? = null
    var animDuration = 120L
    var useContentBgWhenLoading = false //是否在Loading状态使用内容View的背景
    var enableLoadingShadow = false //是否启用加载状态时的半透明阴影
    var emptyText: String = ""
    var emptyIcon: Int = 0
    var enableTouchWhenLoading = false
    var defaultShowLoading = false
    var noEmptyAndError = false //是否去除empty和error状态，有时候只需要一个loading状态，这样减少内存
    var showLoadingOnce = false //是否只显示一次Loading
    var loadingLayoutId = 0
    var emptyLayoutId = 0
    var errorLayoutId = 0
    private var hasShowLoading = false
    private val mHandler = Handler(Looper.getMainLooper())

    private var originErrorDraw: Drawable? = null
    private var originErrorStr: String? = null
    private var originErrorWidth: Int = -1
    private var originErrorHeight: Int = -1

    private var originEmptyDraw: Drawable? = null
    private var originEmptyStr: String? = null
    private var originEmptyWidth: Int = -1
    private var originEmptyHeight: Int = -1

    init {
        val ta = context.obtainStyledAttributes(attributeSet, R.styleable.StateLayout)
        loadingLayoutId = ta.getResourceId(R.styleable.StateLayout_sl_loadingLayoutId, StateLayoutConfig.loadingLayoutId)
        emptyLayoutId = ta.getResourceId(R.styleable.StateLayout_sl_emptyLayoutId, StateLayoutConfig.emptyLayoutId)
        errorLayoutId = ta.getResourceId(R.styleable.StateLayout_sl_errorLayoutId, StateLayoutConfig.errorLayoutId)
        animDuration = ta.getInt(R.styleable.StateLayout_sl_animDuration, StateLayoutConfig.animDuration.toInt()).toLong()
        useContentBgWhenLoading = ta.getBoolean(R.styleable.StateLayout_sl_useContentBgWhenLoading, StateLayoutConfig.useContentBgWhenLoading)
        enableLoadingShadow = ta.getBoolean(R.styleable.StateLayout_sl_enableLoadingShadow, StateLayoutConfig.enableLoadingShadow)
        enableTouchWhenLoading = ta.getBoolean(R.styleable.StateLayout_sl_enableTouchWhenLoading, StateLayoutConfig.enableTouchWhenLoading)
        defaultShowLoading = ta.getBoolean(R.styleable.StateLayout_sl_defaultShowLoading, StateLayoutConfig.defaultShowLoading)
        noEmptyAndError = ta.getBoolean(R.styleable.StateLayout_sl_noEmptyAndError, StateLayoutConfig.noEmptyAndError)
        showLoadingOnce = ta.getBoolean(R.styleable.StateLayout_sl_showLoadingOnce, StateLayoutConfig.showLoadingOnce)
        emptyText = ta.getString(R.styleable.StateLayout_sl_emptyText) ?: StateLayoutConfig.emptyText
        emptyIcon = ta.getResourceId(R.styleable.StateLayout_sl_emptyIcon, StateLayoutConfig.emptyIcon)

        ta.recycle()
    }

    fun wrap(view: View?): StateLayout {
        if (view == null) {
            throw IllegalArgumentException("view can not be null")
        }

        setLoadingLayout()
        setEmptyLayout()
        setErrorLayout()

        view.visibility = View.INVISIBLE
        view.alpha = 0f
        if (view.parent == null) {
            //no attach parent.
            addView(view, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            contentView = view
        } else {
            // 1.remove self from parent
            val parent = view.parent as ViewGroup
            val lp = view.layoutParams
            val index = parent.indexOfChild(view)
            parent.removeView(view)
            // 2.wrap view as a parent
            addView(view, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

            // 3.add this to original parent，暂不支持parent为ConstraintLayout
            parent.addView(this, index, lp)
            contentView = view
        }
        switchLayout(if (defaultShowLoading) Loading else Content)
        return this
    }

    fun wrap(activity: Activity): StateLayout = wrap((activity.findViewById<View>(android.R.id.content) as ViewGroup).getChildAt(0))

    fun wrap(fragment: Fragment): StateLayout = wrap(fragment.view)

    override fun onFinishInflate() {
        super.onFinishInflate()
        if (childCount > 0) {
            contentView = getChildAt(0)
            setLoadingLayout()
            setEmptyLayout()
            setErrorLayout()
            switchLayout(if (defaultShowLoading) Loading else Content)
        }
    }

    private fun switchLayout(s: State) {
        if(state==s)return
        state = s
        when (state) {
            Loading -> {
                switch(loadingView)
                if (useContentBgWhenLoading && contentView?.background != null) {
                    background = contentView?.background
                }
                if (enableLoadingShadow) {
                    loadingView?.setBackgroundColor(Color.parseColor("#88000000"))
                } else {
                    loadingView?.setBackgroundResource(0)
                }
            }
            Empty -> {
                switch(emptyView)
            }
            Error -> {
                switch(errorView)
            }
            Content -> {
                if(contentView?.visibility==VISIBLE && loadingView?.visibility!=VISIBLE
                        && emptyView?.visibility!=VISIBLE && errorView?.visibility!=VISIBLE)return
                switch(contentView)
            }
        }
    }

    fun showLoading(): StateLayout {
        if(showLoadingOnce && hasShowLoading) return this
        mHandler.post {
            switchLayout(Loading)
            if(showLoadingOnce) hasShowLoading = true
        }
        return this
    }

    fun showContent(): StateLayout {
        mHandler.post { switchLayout(Content) }
        return this
    }

    fun showEmptyImgTV(@DrawableRes img: Int, str: String): StateLayout{
        try {
            var tempEmptyDraw = context.resources.getDrawable(img)
            return showEmptyImgTV(tempEmptyDraw, str, -1, -1)
        }catch (e : Resources.NotFoundException){
            throw e
        }
    }

    fun showEmptyImgTV(@DrawableRes img: Int, str: String,  width: Int = -1, height: Int = -1): StateLayout{
        try {
            var tempEmptyDraw = context.resources.getDrawable(img)
            return showEmptyImgTV(tempEmptyDraw, str, width, height)
        }catch (e : Resources.NotFoundException){
            throw e
        }
    }

    fun showEmptyImgTV(@DrawableRes img: Int, @StringRes str: Int):StateLayout{
        try {
            var tempEmptyDraw = context.resources.getDrawable(img)
            var tempEmptyStr = context.resources.getString(str)
            return showEmptyImgTV(tempEmptyDraw, tempEmptyStr, -1, -1)
        }catch (e : Resources.NotFoundException){
            throw e
        }
    }

    fun showEmptyImgTV(@DrawableRes img: Int, @StringRes str: Int, width: Int = -1, height: Int = -1):StateLayout{
        try {
            var tempEmptyDraw = context.resources.getDrawable(img)
            var tempEmptyStr = context.resources.getString(str)
            return showEmptyImgTV(tempEmptyDraw, tempEmptyStr, width, height)
        }catch (e : Resources.NotFoundException){
            throw e
        }
    }

    fun showEmptyImgTV(imgDrawable: Drawable, str: String, width: Int = -1, height: Int = -1): StateLayout{
        //复原样式
        recoverEmptyOriginUI()
        if(emptyView != null){
            val emptyImg = emptyView!!.findViewById<View>(R.id.sl_empty_img)
            val emptyTv = emptyView!!.findViewById<View>(R.id.sl_empty_tv)
            if(emptyImg is ImageView){
                emptyImg.setImageDrawable(imgDrawable)
                val errorLayoutParams = emptyImg.layoutParams
                if(width != -1){
                    errorLayoutParams.width = width
                }
                if(height != -1){
                    errorLayoutParams.height = height
                }
                emptyImg.layoutParams = errorLayoutParams
            }

            if(emptyTv is TextView){
                emptyTv.text = str
            }
        }
        return showEmptyLayout()
    }

    private fun showEmptyLayout(): StateLayout{
        mHandler.post {
            if(noEmptyAndError) {
                switchLayout(Content)
            }else{
                switchLayout(Empty)
            }
        }
        return this
    }

    fun showEmpty(): StateLayout {
        recoverEmptyOriginUI()
        return showEmptyLayout()
    }

    private fun recoverEmptyOriginUI() {
        var emptyImg: View? = null
        if(originEmptyDraw != null && emptyView != null){
            emptyImg = emptyView!!.findViewById<View>(R.id.sl_empty_img)
            if(emptyImg is ImageView){
                emptyImg.setImageDrawable(originEmptyDraw)
            }
        }
        if(originEmptyStr != null && emptyView != null){
            val slEmptyTv = emptyView!!.findViewById<View>(R.id.sl_empty_tv)
            if(slEmptyTv is TextView){
                slEmptyTv.text = originEmptyStr
            }
        }
        val emptyLayoutParams = emptyImg?.layoutParams
        if(originEmptyWidth != -1){
            emptyLayoutParams?.width = originEmptyWidth
        }
        if (originEmptyHeight != -1){
            emptyLayoutParams?.height = originEmptyHeight
        }
        emptyImg?.layoutParams = emptyLayoutParams
    }

    fun showErrorImgTV(@DrawableRes img: Int, str: String): StateLayout{
        try {
            var tempErrorDraw = context.resources.getDrawable(img)
            return showErrorImgTV(tempErrorDraw, str, -1, -1)
        }catch (e : Resources.NotFoundException){
            throw e
        }
    }

    fun showErrorImgTV(@DrawableRes img: Int, str: String,  width: Int = -1, height: Int = -1): StateLayout{
        try {
            var tempErrorDraw = context.resources.getDrawable(img)
            return showErrorImgTV(tempErrorDraw, str, width, height)
        }catch (e : Resources.NotFoundException){
            throw e
        }
    }

    fun showErrorImgTV(@DrawableRes img: Int, @StringRes str: Int):StateLayout{
        try {
            var tempErrorDraw = context.resources.getDrawable(img)
            var tempErrorStr = context.resources.getString(str)
            return showErrorImgTV(tempErrorDraw, tempErrorStr, -1, -1)
        }catch (e : Resources.NotFoundException){
            throw e
        }
    }

    fun showErrorImgTV(@DrawableRes img: Int, @StringRes str: Int, width: Int = -1, height: Int = -1):StateLayout{
        try {
            var tempErrorDraw = context.resources.getDrawable(img)
            var tempErrorStr = context.resources.getString(str)
            return showErrorImgTV(tempErrorDraw, tempErrorStr, width, height)
        }catch (e : Resources.NotFoundException){
            throw e
        }
    }

    fun showErrorImgTV(imgDrawable: Drawable, str: String, width: Int = -1, height: Int = -1): StateLayout{
        //复原样式
        recoverErrorOriginUI()
        if(errorView != null){
            val errorImg = errorView!!.findViewById<View>(R.id.sl_error_img)
            val errorTv = errorView!!.findViewById<View>(R.id.sl_error_tv)
            if(errorImg is ImageView){
                errorImg.setImageDrawable(imgDrawable)
                val errorLayoutParams = errorImg.layoutParams
                if(width != -1){
                    errorLayoutParams.width = width
                }
                if(height != -1){
                    errorLayoutParams.height = height
                }
                errorImg.layoutParams = errorLayoutParams
            }

            if(errorTv is TextView){
                errorTv.text = str
            }
        }
        return showErrorLayout()
    }

    private fun showErrorLayout(): StateLayout{
        mHandler.post {
            if(noEmptyAndError) {
                switchLayout(Content)
            }else{
                switchLayout(Error)
            }
        }
        return this
    }

    fun showError(): StateLayout {
        //复原样式
        recoverErrorOriginUI()
        showErrorLayout()
        return this
    }

    private fun recoverErrorOriginUI() {
        var errorImg: View? = null
        if(originErrorDraw != null && errorView != null){
           errorImg = errorView!!.findViewById<View>(R.id.sl_error_img)
            if(errorImg is ImageView){
                errorImg.setImageDrawable(originErrorDraw)
            }
        }
        if(originErrorStr != null && errorView != null){
            val slErrorTv = errorView!!.findViewById<View>(R.id.sl_error_tv)
            if(slErrorTv is TextView){
                slErrorTv.text = originErrorStr
            }
        }
        val errorLayoutParams = errorImg?.layoutParams
        if(originErrorWidth != -1){
            errorLayoutParams?.width = originErrorWidth
        }
        if (originErrorHeight != -1){
            errorLayoutParams?.height = originErrorHeight
        }
        errorImg?.layoutParams = errorLayoutParams
    }

    private fun switch(v: View?) {
        if (switchTask != null) {
            mHandler.removeCallbacks(switchTask!!)
        }
        switchTask = SwitchTask(v)
        mHandler.post(switchTask!!)
    }

    private fun retry() {
        if (errorView == null) return
        hasShowLoading = false
        showLoading()
        mHandler.postDelayed({
            mRetryAction?.invoke(errorView!!)
        }, animDuration)
    }

    private fun refresh() {
        if (emptyView == null || mRefreshAction == null) return
        hasShowLoading = false
        showLoading()
        mHandler.postDelayed({
            mRefreshAction?.invoke(emptyView!!)
        }, animDuration)
    }



    var switchTask: SwitchTask? = null

    inner class SwitchTask(private var target: View?) : Runnable {
        override fun run() {
            for (i in 0..childCount) {
                if (state == Loading && enableLoadingShadow && getChildAt(i) == contentView) continue
                hideAnim(getChildAt(i))
            }
            showAnim(target)
        }
    }

    private fun showAnim(v: View?) {
        if (v == null) return
        v.animate().cancel()
        v.animate().alpha(1f).setDuration(animDuration)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        v.visibility = View.VISIBLE
                    }
                })
                .start()
    }

    private fun hideAnim(v: View?) {
        if (v == null) return
        v.animate().cancel()
        v.animate().alpha(0f).setDuration(animDuration)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        v.visibility = if(v==contentView) View.INVISIBLE else View.GONE
                    }
                })
                .start()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (state == Loading && loadingView?.visibility == View.VISIBLE && !enableTouchWhenLoading) return true
        return super.dispatchTouchEvent(ev)
    }

    var mRetryAction: ((errView: View) -> Unit)? = null
    var mRefreshAction: ((emptyView: View) -> Unit)? = null

    /**
     * 设置加载中的布局
     */
    private fun setLoadingLayout(): StateLayout {
        if (loadingView?.parent != null) removeView(loadingView)
        loadingView = LayoutInflater.from(context).inflate(loadingLayoutId, this, false)
        loadingView?.apply {
            (layoutParams as LayoutParams).gravity = Gravity.CENTER
            visibility = View.GONE
            alpha = 0f
            addView(loadingView)
        }
        return this
    }

    /**
     * 设置数据为空的布局
     */
    private fun setEmptyLayout(): StateLayout {
        if(noEmptyAndError)return this
        if (emptyView?.parent != null) removeView(emptyView)
        emptyView = LayoutInflater.from(context).inflate(emptyLayoutId, this, false)
        emptyView?.apply {
            (layoutParams as LayoutParams).gravity = Gravity.CENTER
            visibility = View.GONE
            alpha = 0f
            addView(emptyView)
            saveEmptyOriginImgTv()
            //智能设置文字和图标
            if(emptyView!=null && emptyView is ViewGroup){
                val group = emptyView as ViewGroup
                (0 until group.childCount).forEach {
                    val child = group.getChildAt(it)
                    if(child is TextView && !emptyText.isNullOrEmpty()) {
                        child.text = emptyText
                    }else if(child is ImageView && emptyIcon!=0){
                        child.setImageResource(emptyIcon)
                    }
                }
            }
            setOnClickListener { refresh() }
        }
        return this
    }

    private fun saveEmptyOriginImgTv() {
        val emptyImg = emptyView!!.findViewById<View>(R.id.sl_empty_img)
        val emptyTv = emptyView!!.findViewById<View>(R.id.sl_empty_tv)
        if(emptyImg is ImageView){
            originEmptyDraw = emptyImg.drawable
            val emptyLayoutParams = emptyImg.layoutParams
            if(width != -1){
                originEmptyWidth = emptyLayoutParams.width
            }
            if(height != -1){
                originEmptyHeight = emptyLayoutParams.height
            }
        }
        if(emptyTv is TextView){
            originEmptyStr = emptyTv.text.toString()
        }
    }

    /**
     * 设置加载失败的布局
     */
    private fun setErrorLayout(): StateLayout {
        if(noEmptyAndError)return this
        if (errorView?.parent != null) removeView(errorView)
        errorView = LayoutInflater.from(context).inflate(errorLayoutId, this, false)
        errorView?.apply {
            (layoutParams as LayoutParams).gravity = Gravity.CENTER
            visibility = View.GONE
            alpha = 0f
            setOnClickListener { retry() }
            addView(errorView)
            saveErrorOriginImgTv()
        }
        return this
    }

    private fun saveErrorOriginImgTv() {
        val errorImg = errorView!!.findViewById<View>(R.id.sl_error_img)
        val errorTv = errorView!!.findViewById<View>(R.id.sl_error_tv)
        if(errorImg is ImageView){
            originErrorDraw = errorImg.drawable
            val errorLayoutParams = errorImg.layoutParams
            if(width != -1){
                originErrorWidth = errorLayoutParams.width
            }
            if(height != -1){
                originErrorHeight = errorLayoutParams.height
            }
        }
        if(errorTv is TextView){
            originErrorStr = errorTv.text.toString()
        }
    }

    /**
     * 自定义一些配置
     * @param loadingLayoutId 加载时的布局
     * @param emptyLayoutId 数据为空时的布局
     * @param errorLayoutId 加载失败的布局
     * @param useContentBgWhenLoading 是否在加载状态下使用contentView的背景
     * @param animDuration 遮照显示和隐藏的动画时长
     * @param enableLoadingShadow 是否启用加载时的半透明阴影
     * @param enableTouchWhenLoading 是否在加载时允许触摸下层View
     * @param retryAction 加载失败状态下点击重试的行为
     */
    fun config(loadingLayoutId: Int? = null,
               emptyLayoutId: Int? = null,
               errorLayoutId: Int? = null,
               emptyText: String? = null,
               emptyIcon: Int? = null,
               useContentBgWhenLoading: Boolean? = null,
               animDuration: Long? = null,
               noEmptyAndError: Boolean? = null,
               defaultShowLoading: Boolean? = null,
               enableLoadingShadow: Boolean? = null,
               enableTouchWhenLoading: Boolean? = null,
               showLoadingOnce: Boolean? = null,
               refreshAction:  ((errView: View) -> Unit)? = null,
               retryAction: ((errView: View) -> Unit)? = null): StateLayout {
        if(emptyText!=null) this.emptyText = emptyText
        if(emptyIcon!=null) this.emptyIcon = emptyIcon
        if(noEmptyAndError!=null) this.noEmptyAndError = noEmptyAndError
        if (loadingLayoutId != null) {
            this.loadingLayoutId = loadingLayoutId
            setLoadingLayout()
        }
        if (emptyLayoutId != null) this.emptyLayoutId  = emptyLayoutId
        if(emptyLayoutId!=null || emptyText!=null || emptyIcon!=null){
            setEmptyLayout()
        }
        if (errorLayoutId != null){
            this.errorLayoutId = errorLayoutId
            setErrorLayout()
        }
        if (useContentBgWhenLoading!=null) {
            this.useContentBgWhenLoading = useContentBgWhenLoading
        }
        if (animDuration != null) {
            this.animDuration = animDuration
        }
        if(defaultShowLoading!=null) this.defaultShowLoading = defaultShowLoading
        if(enableLoadingShadow!=null) this.enableLoadingShadow = enableLoadingShadow
        if(enableTouchWhenLoading!=null) this.enableTouchWhenLoading = enableTouchWhenLoading
        if(showLoadingOnce!=null) this.showLoadingOnce = showLoadingOnce
        if(retryAction!=null)mRetryAction = retryAction
        if(refreshAction!=null)mRefreshAction = refreshAction
        return this
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mHandler.removeCallbacksAndMessages(null)
    }
}