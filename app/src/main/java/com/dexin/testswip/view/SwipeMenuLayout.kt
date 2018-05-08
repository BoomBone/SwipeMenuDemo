package com.dexin.testswip.view

import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Scroller
import com.dexin.testswip.R
import java.util.ArrayList

/**
 * @author Ting
 * @date 2018/5/8.
 */
class SwipeMenuLayout(context: Context, attrs: AttributeSet, defStyleAttr: Int)
    : ViewGroup(context, attrs, defStyleAttr) {

    private var mScaledTouchSlop: Int = 0
    private var mScroller: Scroller? = null

    private var mLeftViewResID: Int = 0
    private var mRightViewResID: Int = 0
    private var mContentViewResID: Int = 0

    private var mFraction = 0.3f
    private var mCanLeftSwipe = true
    private var mCanRightSwipe = true

    private val mMatchParentChildren = ArrayList<View>(1)

    private var mLeftView: View? = null
    private var mRightView: View? = null
    private var mContentView: View? = null
    private var mContentViewLp: ViewGroup.MarginLayoutParams? = null

    private var isSwipeing: Boolean = false
    private var mLastP: PointF? = null
    private var mFirstP: PointF? = null

    private var mViewCache: SwipeMenuLayout? = null
    private var mStateCache: State = State.CLOSE
    private var finalyDistanceX: Float = 0.toFloat()

    private var result: State = State.CLOSE

    init {
        //创建辅助对象
        val viewConfiguration = ViewConfiguration.get(context)
        mScaledTouchSlop = viewConfiguration.scaledTouchSlop
        mScroller = Scroller(context)
        //1、获取配置的属性值
        val typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.EasySwipeMenuLayout, defStyleAttr, 0)

        try {
            val indexCount = typedArray.indexCount
            for (i in 0 until indexCount) {
                val attr = typedArray.getIndex(i)
                when (attr) {
                    R.styleable.EasySwipeMenuLayout_leftMenuView ->
                        mLeftViewResID = typedArray.getResourceId(R.styleable.EasySwipeMenuLayout_leftMenuView, -1)
                    R.styleable.EasySwipeMenuLayout_rightMenuView ->
                        mRightViewResID = typedArray.getResourceId(R.styleable.EasySwipeMenuLayout_rightMenuView, -1)
                    R.styleable.EasySwipeMenuLayout_contentView ->
                        mContentViewResID = typedArray.getResourceId(R.styleable.EasySwipeMenuLayout_contentView, -1)
                    R.styleable.EasySwipeMenuLayout_canLeftSwipe ->
                        mCanLeftSwipe = typedArray.getBoolean(R.styleable.EasySwipeMenuLayout_canLeftSwipe, true)
                    R.styleable.EasySwipeMenuLayout_canRightSwipe ->
                        mCanRightSwipe = typedArray.getBoolean(R.styleable.EasySwipeMenuLayout_canRightSwipe, true)
                    R.styleable.EasySwipeMenuLayout_fraction ->
                        mFraction = typedArray.getFloat(R.styleable.EasySwipeMenuLayout_fraction, 0.5f)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            typedArray.recycle()
        }

    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val count = childCount
        val left = 0 + paddingLeft
        val right = 0 + paddingLeft
        val top = 0 + paddingTop
        val bottom = 0 + paddingTop

        for (i in 0 until count) {
            val child = getChildAt(i)
            if (mLeftView == null && child.id == mLeftViewResID) {
                // Log.i(TAG, "找到左边按钮view");
                mLeftView = child
                mLeftView?.setClickable(true)
            } else if (mRightView == null && child.id == mRightViewResID) {
                // Log.i(TAG, "找到右边按钮view");
                mRightView = child
                mRightView?.setClickable(true)
            } else if (mContentView == null && child.id == mContentViewResID) {
                // Log.i(TAG, "找到内容View");
                mContentView = child
                mContentView?.setClickable(true)
            }

        }
        //布局contentView
        var cRight = 0
        if (mContentView != null) {
            mContentViewLp = mContentView?.getLayoutParams() as ViewGroup.MarginLayoutParams
            val cTop = top + mContentViewLp!!.topMargin
            val cLeft = left + mContentViewLp!!.leftMargin
            cRight = left + mContentViewLp!!.leftMargin + mContentView!!.getMeasuredWidth()
            val cBottom = cTop + mContentView!!.getMeasuredHeight()
            mContentView!!.layout(cLeft, cTop, cRight, cBottom)
        }
        if (mLeftView != null) {
            val leftViewLp = mLeftView!!.getLayoutParams() as ViewGroup.MarginLayoutParams
            val lTop = top + leftViewLp.topMargin
            val lLeft = 0 - mLeftView!!.getMeasuredWidth() + leftViewLp.leftMargin + leftViewLp.rightMargin
            val lRight = 0 - leftViewLp.rightMargin
            val lBottom = lTop + mLeftView!!.getMeasuredHeight()
            mLeftView!!.layout(lLeft, lTop, lRight, lBottom)
        }
        if (mRightView != null && mContentView != null) {
            val rightViewLp = mRightView!!.getLayoutParams() as ViewGroup.MarginLayoutParams
            val lTop = top + rightViewLp.topMargin
            val lLeft = mContentView!!.getRight() + mContentViewLp!!.rightMargin + rightViewLp.leftMargin
            val lRight = lLeft + mRightView!!.getMeasuredWidth()
            val lBottom = lTop + mRightView!!.getMeasuredHeight()
            mRightView!!.layout(lLeft, lTop, lRight, lBottom)
        }

    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        //获取childView的个数
        isClickable = true
        var count = childCount
        //参考frameLayout测量代码
        val measureMatchParentChildren =
                View.MeasureSpec.getMode(widthMeasureSpec) != View.MeasureSpec.EXACTLY ||
                        View.MeasureSpec.getMode(heightMeasureSpec) != View.MeasureSpec.EXACTLY
        mMatchParentChildren.clear()
        var maxHeight = 0
        var maxWidth = 0
        var childState = 0
        //遍历childViews
        for (i in 0 until count) {
            val child = getChildAt(i)

            if (child.visibility != View.GONE) {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
                val lp = child.layoutParams as ViewGroup.MarginLayoutParams
                maxWidth = Math.max(maxWidth,
                        child.measuredWidth + lp.leftMargin + lp.rightMargin)
                maxHeight = Math.max(maxHeight,
                        child.measuredHeight + lp.topMargin + lp.bottomMargin)
                childState = View.combineMeasuredStates(childState, child.measuredState)
                if (measureMatchParentChildren) {
                    if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT ||
                            lp.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                        mMatchParentChildren.add(child)
                    }
                }
            }
        }
        // Check against our minimum height and width
        maxHeight = Math.max(maxHeight, suggestedMinimumHeight)
        maxWidth = Math.max(maxWidth, suggestedMinimumWidth)
        setMeasuredDimension(View.resolveSizeAndState(maxWidth, widthMeasureSpec, childState),
                View.resolveSizeAndState(maxHeight, heightMeasureSpec,
                        childState shl View.MEASURED_HEIGHT_STATE_SHIFT))

        count = mMatchParentChildren.size
        if (count > 1) {
            for (i in 0 until count) {
                val child = mMatchParentChildren.get(i)
                val lp = child.getLayoutParams() as ViewGroup.MarginLayoutParams

                val childWidthMeasureSpec: Int
                if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                    val width = Math.max(0, measuredWidth
                            - lp.leftMargin - lp.rightMargin)
                    childWidthMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                            width, View.MeasureSpec.EXACTLY)
                } else {
                    childWidthMeasureSpec = ViewGroup.getChildMeasureSpec(widthMeasureSpec,
                            lp.leftMargin + lp.rightMargin,
                            lp.width)
                }

                val childHeightMeasureSpec: Int
                if (lp.height == FrameLayout.LayoutParams.MATCH_PARENT) {
                    val height = Math.max(0, measuredHeight
                            - lp.topMargin - lp.bottomMargin)
                    childHeightMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                            height, View.MeasureSpec.EXACTLY)
                } else {
                    childHeightMeasureSpec = ViewGroup.getChildMeasureSpec(heightMeasureSpec,
                            lp.topMargin + lp.bottomMargin,
                            lp.height)
                }

                child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
            }
        }
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return MarginLayoutParams(context, attrs)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.getAction()) {
            MotionEvent.ACTION_DOWN -> {
                //   System.out.println(">>>>dispatchTouchEvent() ACTION_DOWN");
                isSwipeing = false
                if (mLastP == null) {
                    mLastP = PointF()
                }
                mLastP?.set(ev.getRawX(), ev.getRawY())
                if (mFirstP == null) {
                    mFirstP = PointF()
                }
                mFirstP?.set(ev.getRawX(), ev.getRawY())
                if (mViewCache != null) {
                    if (mViewCache !== this) {
                        mViewCache?.handlerSwipeMenu(State.CLOSE)
                    }
                    // Log.i(TAG, ">>>有菜单被打开");
                    parent.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                //   System.out.println(">>>>dispatchTouchEvent() ACTION_MOVE getScrollX:" + getScrollX());
                val distanceX = mLastP!!.x - ev.rawX
                val distanceY = mLastP!!.y - ev.rawY
                if (Math.abs(distanceY) > mScaledTouchSlop && Math.abs(distanceY) > Math.abs(distanceX)) {
                } else {
                    //滑动使用scrollBy
                    scrollBy(distanceX.toInt(), 0)
                    //越界修正
                    if (scrollX < 0) {
                        if (!mCanRightSwipe || mLeftView == null) {
                            scrollTo(0, 0)
                        } else {//左滑
                            if (mLeftView != null && scrollX < mLeftView!!.getLeft()) {
                                scrollTo(mLeftView!!.left, 0)
                            }

                        }
                    } else if (scrollX > 0) {
                        if (!mCanLeftSwipe || mRightView == null) {
                            scrollTo(0, 0)
                        } else {
                            if (mRightView != null && mContentView != null && mContentViewLp != null &&
                                    scrollX > mRightView!!.right - mContentView!!.right - mContentViewLp!!.rightMargin) {
                                scrollTo(mRightView!!.right - mContentView!!.right - mContentViewLp!!.rightMargin, 0)
                            }
                        }
                    }
                    //当处于水平滑动时，禁止父类拦截
                    if (Math.abs(distanceX) > mScaledTouchSlop) {
                        //  Log.i(TAG, ">>>>当处于水平滑动时，禁止父类拦截 true");
                        parent.requestDisallowInterceptTouchEvent(true)
                    }//                        || Math.abs(getScrollX()) > mScaledTouchSlop
                    mLastP?.set(ev.rawX, ev.rawY)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                //     System.out.println(">>>>dispatchTouchEvent() ACTION_CANCEL OR ACTION_UP");

                finalyDistanceX = mFirstP!!.x - ev.rawX
                if (Math.abs(finalyDistanceX) > mScaledTouchSlop) {
                    //  System.out.println(">>>>P");

                    isSwipeing = true
                }
                result = isShouldOpen(scrollX)
                handlerSwipeMenu(result)
            }
            else -> {
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * 根据当前的scrollX的值判断松开手后应处于何种状态
     *
     * @param
     * @param scrollX
     * @return
     */
    private fun isShouldOpen(scrollX: Int): State {
        if (mScaledTouchSlop >= Math.abs(finalyDistanceX)) {
            return mStateCache
        }
        if (finalyDistanceX < 0) {
            //➡滑动
            //1、展开左边按钮
            //获得leftView的测量长度
            if (getScrollX() < 0 && mLeftView != null) {
                if (Math.abs(mLeftView!!.getWidth() * mFraction) < Math.abs(getScrollX())) {
                    return State.LEFTOPEN
                }
            }
            //2、关闭右边按钮

            if (getScrollX() > 0 && mRightView != null) {
                return State.CLOSE
            }
        } else if (finalyDistanceX > 0) {
            //⬅️滑动
            //3、开启右边菜单按钮
            if (getScrollX() > 0 && mRightView != null) {

                if (Math.abs(mRightView!!.getWidth() * mFraction) < Math.abs(getScrollX())) {
                    return State.RIGHTOPEN
                }
            }
            //关闭左边
            if (getScrollX() < 0 && mLeftView != null) {
                return State.CLOSE
            }
        }

        return State.CLOSE

    }

    /**
     * 自动设置状态
     *
     * @param result
     */

    private fun handlerSwipeMenu(result: State) {
        if (result === State.LEFTOPEN) {
            mScroller?.startScroll(scrollX, 0, mLeftView!!.getLeft() - scrollX, 0)
            mViewCache = this
            mStateCache = result
        } else if (result === State.RIGHTOPEN) {
            mViewCache = this
            mScroller?.startScroll(scrollX, 0, mRightView!!.getRight() - mContentView!!.getRight() - mContentViewLp!!.rightMargin - scrollX, 0)
            mStateCache = result
        } else {
            mScroller?.startScroll(scrollX, 0, -scrollX, 0)
            mViewCache = null
            mStateCache = result
        }
        invalidate()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        //  Log.d(TAG, "<<<<dispatchTouchEvent() called with: " + "ev = [" + event + "]");
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
            }
            MotionEvent.ACTION_MOVE -> {
                //滑动时拦截点击时间
                if (Math.abs(finalyDistanceX) > mScaledTouchSlop) {
                    // 当手指拖动值大于mScaledTouchSlop值时，认为应该进行滚动，拦截子控件的事件
                    //   Log.i(TAG, "<<<onInterceptTouchEvent true");
                    return true
                }

            }//                if (Math.abs(finalyDistanceX) > mScaledTouchSlop || Math.abs(getScrollX()) > mScaledTouchSlop) {
        //                    Log.d(TAG, "onInterceptTouchEvent: 2");
        //                    return true;
        //                }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                //滑动后不触发contentView的点击事件
                if (isSwipeing) {
                    isSwipeing = false
                    finalyDistanceX = 0f
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun computeScroll() {
        //判断Scroller是否执行完毕：
        if (mScroller != null) {
            if (mScroller!!.computeScrollOffset()) {
                scrollTo(mScroller!!.getCurrX(), mScroller!!.getCurrY())
                //通知View重绘-invalidate()->onDraw()->computeScroll()
                invalidate()
            }
        }
    }

    override fun onDetachedFromWindow() {
        if (this === mViewCache) {
            mViewCache!!.handlerSwipeMenu(State.CLOSE)
        }
        super.onDetachedFromWindow()
        //  Log.i(TAG, ">>>>>>>>onDetachedFromWindow");

    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (this === mViewCache) {
            mViewCache!!.handlerSwipeMenu(mStateCache)
        }
        // Log.i(TAG, ">>>>>>>>onAttachedToWindow");
    }

    fun resetStatus() {
        if (mViewCache != null) {
            if (mStateCache != null && mStateCache !== State.CLOSE && mScroller != null) {
                mScroller!!.startScroll(mViewCache!!.getScrollX(), 0, -mViewCache!!.getScrollX(), 0)
                mViewCache!!.invalidate()
                mViewCache = null
                mStateCache = result
            }
        }
    }


    fun getFraction(): Float {
        return mFraction
    }

    fun setFraction(mFraction: Float) {
        this.mFraction = mFraction
    }

    fun isCanLeftSwipe(): Boolean {
        return mCanLeftSwipe
    }

    fun setCanLeftSwipe(mCanLeftSwipe: Boolean) {
        this.mCanLeftSwipe = mCanLeftSwipe
    }

    fun isCanRightSwipe(): Boolean {
        return mCanRightSwipe
    }

    fun setCanRightSwipe(mCanRightSwipe: Boolean) {
        this.mCanRightSwipe = mCanRightSwipe
    }
}