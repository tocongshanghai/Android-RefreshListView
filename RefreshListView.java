package com.example.refreshlistview;


import java.text.SimpleDateFormat;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 
 * 下拉刷新 上拉加载
 * 
 * @author smt
 * 2016-4-11 11:00
 * 
 */
public class RefreshListView extends ListView implements OnScrollListener {

	private int firstVisibleItemPosition; // 屏幕显示在第一个的item的索引
	private int downY=-1; // 按下时y轴的偏移量
	private int headerViewHeight; // 头布局的高度
	private View headerView; // 头布局的对象

	private final int DOWN_PULL_REFRESH = 0; // 下拉刷新状态
	private final int RELEASE_REFRESH = 1; // 松开刷新
	private final int REFRESHING = 2; // 正在刷新中
	private int currentState = DOWN_PULL_REFRESH; // 头布局的状态: 默认为下拉刷新状态

	private Animation upAnimation; // 向上旋转的动画
	private Animation downAnimation; // 向下旋转的动画

	private ImageView ivArrow; // 头布局的剪头
	private ProgressBar mProgressBar; // 头布局的进度条
	private TextView tvState; // 头布局的状态
	private TextView tvLastUpdateTime; // 头布局的最后更新时间

	private OnRefreshListener mOnRefershListener;
	private boolean isScrollToBottom; // 是否滑动到底部
	private View footerView; // 脚布局的对象
	private int footerViewHeight; // 脚布局的高度
	private boolean isLoadingMore = false; // 是否正在加载更多中
	private TextView footerText;// 底部布局文本
	private ProgressBar footerPb;// 进度条

	public RefreshListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initHeaderView();
		initFooterView();
		this.setOnScrollListener(this);
	}

	/**
	 * 初始化脚布局
	 */
	private void initFooterView() {
		footerView = View.inflate(getContext(), R.layout.foot, null);
		footerText = (TextView) footerView
				.findViewById(R.id.xlistview_footer_hint_textview);
		footerPb = (ProgressBar) footerView
				.findViewById(R.id.xlistview_footer_progressbar);
		footerView.measure(0, 0);
		footerViewHeight = footerView.getMeasuredHeight();
		footerView.setPadding(0, -footerViewHeight, 0, 0);
		this.addFooterView(footerView);
	}

	/**
	 * 初始化头布局
	 */
	private void initHeaderView() {
		headerView = View.inflate(getContext(), R.layout.xlistview_header, null);
		ivArrow = (ImageView) headerView
				.findViewById(R.id.xlistview_header_arrow);
		mProgressBar = (ProgressBar) headerView
				.findViewById(R.id.xlistview_header_progressbar);
		tvState = (TextView) headerView
				.findViewById(R.id.xlistview_header_hint_textview);
		tvLastUpdateTime = (TextView) headerView
				.findViewById(R.id.xlistview_header_time);

		// 设置最后刷新时间
		tvLastUpdateTime.setText("" + getLastUpdateTime());

		headerView.measure(0, 0); //测量出headerView的高度
		headerViewHeight = headerView.getMeasuredHeight();
		headerView.setPadding(0, -headerViewHeight, 0, 0);
		this.addHeaderView(headerView); // 向ListView的顶部添加一个view对象
		initAnimation();
	}

	/**
	 * 获得系统的最新时间
	 * 
	 * @return
	 */
	private String getLastUpdateTime() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		return sdf.format(System.currentTimeMillis());
	}

	/**
	 * 初始化动画
	 */
	private void initAnimation() {
		upAnimation = new RotateAnimation(0f, -180f,
				Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
				0.5f);
		upAnimation.setDuration(500);
		upAnimation.setFillAfter(true); // 动画结束后, 停留在结束的位置上

		downAnimation = new RotateAnimation(-180f, -360f,
				Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
				0.5f);
		downAnimation.setDuration(500);
		downAnimation.setFillAfter(true); // 动画结束后, 停留在结束的位置上
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		//有的时候可能会进不去down，所以在外面获取一下
		if (downY == -1) {
			downY = (int) ev.getY();
			
		}
		switch (ev.getAction()) {
		case MotionEvent.ACTION_DOWN:
			downY = (int) ev.getY();
			break;
		case MotionEvent.ACTION_MOVE:
			int moveY = (int) ev.getY();
			// 移动中的y - 按下的y = 间距.
			int diff = (moveY - downY);

				// -头布局的高度 + 间距 = paddingTop
				int paddingTop = -headerViewHeight + diff;
				
				// 如果: -头布局的高度 > paddingTop的值 执行super.onTouchEvent(ev);
				if (firstVisibleItemPosition == 0
						&& -headerViewHeight < paddingTop) {
					if (paddingTop > 0 && currentState == DOWN_PULL_REFRESH) { // 完全显示了.
						currentState = RELEASE_REFRESH;
						refreshHeaderView();
					} else if (paddingTop < 0
							&& currentState == RELEASE_REFRESH) { // 没有显示完全
						currentState = DOWN_PULL_REFRESH;
						refreshHeaderView();
					}
					// 下拉头布局
					headerView.setPadding(0, paddingTop/2, 0, 0);
					return true;
				}
			

			break;
		case MotionEvent.ACTION_UP:
			performClick();
			downY=-1;
			// 判断当前的状态是松开刷新还是下拉刷新
			if (currentState == RELEASE_REFRESH) {
				// 把头布局设置为完全显示状态
				headerView.setPadding(0, 0, 0, 0);
				// 进入到正在刷新中状态
				currentState = REFRESHING;
				refreshHeaderView();

				if (mOnRefershListener != null) {
					mOnRefershListener.onDownPullRefresh(); // 调用使用者的监听方法
				}
			} else if (currentState == DOWN_PULL_REFRESH) {
				// 隐藏头布局
				headerView.setPadding(0, -headerViewHeight, 0, 0);
			}
			
			break;
		default:
			break;
		}
		return super.onTouchEvent(ev);
	}

	/**
	 * 根据currentState刷新头布局的状态
	 */
	private void refreshHeaderView() {
		switch (currentState) {
		case DOWN_PULL_REFRESH: // 下拉刷新状态
			tvState.setText("下拉刷新");
			ivArrow.startAnimation(downAnimation); // 执行向下旋转
			break;
		case RELEASE_REFRESH: // 松开刷新状态
			tvState.setText("松开刷新");
			ivArrow.startAnimation(upAnimation); // 执行向上旋转
			break;
		case REFRESHING: // 正在刷新中状态
			ivArrow.clearAnimation();
			ivArrow.setVisibility(View.GONE);
			mProgressBar.setVisibility(View.VISIBLE);
			tvState.setText("正在刷新中...");
			break;
		default:
			break;
		}
	}

	/**
	 * 当滚动状态改变时回调
	 */
	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {

		if (scrollState == SCROLL_STATE_IDLE
				|| scrollState == SCROLL_STATE_FLING) {
			// 判断当前是否已经到了底部
			if (isScrollToBottom && !isLoadingMore) {
				isLoadingMore = true;
				// 当前到底部
					footerView.setPadding(0, 0, 0, 0);
					this.setSelection(this.getCount());
					footerView.setOnClickListener(new OnClickListener() {

						@Override
						public void onClick(View v) {
							if (mOnRefershListener != null) {
								footerPb.setVisibility(View.VISIBLE);
								footerText.setText("正在加载...");
								mOnRefershListener.onLoadingMore();
							}
						}
					});
				}
		}
	}

	/**
	 * 当滚动时调用
	 * 
	 * @param firstVisibleItem
	 *            当前屏幕显示在顶部的item的position
	 * @param visibleItemCount
	 *            当前屏幕显示了多少个条目的总数
	 * @param totalItemCount
	 *            ListView的总条目的总数
	 */
	@Override
	public void onScroll(AbsListView view, int firstVisibleItem,
			int visibleItemCount, int totalItemCount) {
		firstVisibleItemPosition = firstVisibleItem;

		if (getLastVisiblePosition() == (totalItemCount - 1)) {
			isScrollToBottom = true;
		} else {
			isScrollToBottom = false;
		}
	}

	/**
	 * 设置刷新监听事件
	 * 
	 * @param listener
	 */
	public void setOnRefreshListener(OnRefreshListener listener) {
		mOnRefershListener = listener;
	}

	/**
	 * 隐藏头布局
	 */
	public void hideHeaderView() {
		headerView.setPadding(0, -headerViewHeight, 0, 0);
		ivArrow.setVisibility(View.VISIBLE);
		mProgressBar.setVisibility(View.GONE);
		tvState.setText("下拉刷新");
		tvLastUpdateTime.setText("" + getLastUpdateTime());
		currentState = DOWN_PULL_REFRESH;
	}

	/**
	 * 隐藏脚布局
	 */
	public void hideFooterView() {
		footerPb.setVisibility(View.INVISIBLE);
		footerText.setText("加载更多");
		footerView.setPadding(0, -footerViewHeight, 0, 0);
		isLoadingMore = false;
	}
	/**
	 * listview回调
	 * 
	 * @author smt
	 *
	 */

	public interface OnRefreshListener {

		/**
		 * 下拉刷新
		 */
		void onDownPullRefresh();

		/**
		 * 上拉加载更多
		 */
		void onLoadingMore();
	}
}