package org.koitharu.kotatsu.main.ui.nav

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.koitharu.kotatsu.R

data class FloatingNavBarItem(
	@IdRes val id: Int,
	@StringRes val titleRes: Int,
	@DrawableRes val icon: Int,
	val badgeCount: Int = 0,
)

data class FloatingNavBarColors(
	val container: Int,
	val selectedContainer: Int,
	val selectedContent: Int,
	val unselectedContent: Int,
)

// The M3 Expressive default spatial spring. Snappier than Compose's own default, and shared by the
// icon tint, the label expand and the sibling resize so they all move on one beat.
private val SpringFloat = spring<Float>(dampingRatio = 0.9f, stiffness = 380f)
private val SpringColor = spring<Color>(dampingRatio = 0.9f, stiffness = 380f)
private val SpringSize = spring<IntSize>(dampingRatio = 0.9f, stiffness = 380f)

@Composable
fun FloatingNavBar(
	items: List<FloatingNavBarItem>,
	selectedId: Int,
	showLabels: Boolean,
	colors: FloatingNavBarColors,
	onItemSelected: (Int) -> Unit,
	onItemReselected: (Int) -> Unit,
	modifier: Modifier = Modifier,
	showContinue: Boolean = false,
	onContinueClick: () -> Unit = {},
) {
	if (items.isEmpty()) return
	val selectedTitle = stringResource(items.firstOrNull { it.id == selectedId }?.titleRes ?: items.first().titleRes)
	val labelStyle = MaterialTheme.typography.labelMedium
	val textMeasurer = rememberTextMeasurer()
	val density = LocalDensity.current
	val selectedLabelExtra = with(density) {
		textMeasurer.measure(text = selectedTitle, style = labelStyle).size.width.toDp()
	} + 8.dp

	BoxWithConstraints(
		modifier = modifier.fillMaxWidth(),
		contentAlignment = Alignment.Center,
	) {
		// 48dp per destination is the minimum accessible touch target. Calculate before composing
		// the children so a long translated label cannot consume the Continue button's width.
		val iconOnlyBarWidth = barWidth(items.size, RegularMetrics)
		val continueWidth = 56.dp + 8.dp
		val stackContinue = showContinue && iconOnlyBarWidth + continueWidth > maxWidth
		val availableBarWidth = if (showContinue && !stackContinue) {
			maxWidth - continueWidth
		} else {
			maxWidth
		}
		// With up to eight destinations the regular spacing no longer fits a phone: tighten it first, and when
		// even that is too wide let the pill scroll sideways instead of dropping or squashing items.
		val metrics = if (iconOnlyBarWidth > availableBarWidth) CompactMetrics else RegularMetrics
		val pillWidth = barWidth(items.size, metrics)
		val scrollable = pillWidth > availableBarWidth
		val showSelectedLabel = showLabels &&
			pillWidth + selectedLabelExtra <= availableBarWidth

		if (stackContinue) {
			Column(
				horizontalAlignment = Alignment.End,
				verticalArrangement = Arrangement.spacedBy(4.dp),
			) {
				FloatingContinueVisibility(
					visible = true,
					colors = colors,
					onContinueClick = onContinueClick,
				)
				FloatingNavItemsSurface(
					items = items,
					selectedId = selectedId,
					showSelectedLabel = showSelectedLabel,
					colors = colors,
					metrics = metrics,
					maxBarWidth = if (scrollable) availableBarWidth else null,
					onItemSelected = onItemSelected,
					onItemReselected = onItemReselected,
				)
			}
		} else {
			Row(
				modifier = Modifier.wrapContentWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				FloatingNavItemsSurface(
					items = items,
					selectedId = selectedId,
					showSelectedLabel = showSelectedLabel,
					colors = colors,
					metrics = metrics,
					maxBarWidth = if (scrollable) availableBarWidth else null,
					onItemSelected = onItemSelected,
					onItemReselected = onItemReselected,
				)
				FloatingContinueVisibility(
					visible = showContinue,
					colors = colors,
					onContinueClick = onContinueClick,
				)
			}
		}
	}
}

/** Size of one destination and the gap between destinations on the pill. */
private data class FloatingNavMetrics(
	val itemWidth: Dp,
	val spacing: Dp,
)

private val RegularMetrics = FloatingNavMetrics(itemWidth = 48.dp, spacing = 4.dp)
private val CompactMetrics = FloatingNavMetrics(itemWidth = 44.dp, spacing = 2.dp)

/** Width of an icon-only pill: its 8dp inner padding on both sides plus every item and gap. */
private fun barWidth(count: Int, metrics: FloatingNavMetrics): Dp =
	16.dp + (metrics.itemWidth * count) + (metrics.spacing * (count - 1).coerceAtLeast(0))

@Composable
private fun FloatingNavItemsSurface(
	items: List<FloatingNavBarItem>,
	selectedId: Int,
	showSelectedLabel: Boolean,
	colors: FloatingNavBarColors,
	metrics: FloatingNavMetrics,
	maxBarWidth: Dp?,
	onItemSelected: (Int) -> Unit,
	onItemReselected: (Int) -> Unit,
) {
	val scrollState = rememberScrollState()
	val density = LocalDensity.current
	if (maxBarWidth != null) {
		// Keep the selected destination in view when the pill is too narrow to show every item at once.
		LaunchedEffect(selectedId, maxBarWidth, metrics) {
			val index = items.indexOfFirst { it.id == selectedId }
			if (index < 0) return@LaunchedEffect
			val target = with(density) {
				val itemStart = (8.dp + (metrics.itemWidth + metrics.spacing) * index).roundToPx()
				itemStart - (maxBarWidth.roundToPx() - metrics.itemWidth.roundToPx()) / 2
			}
			scrollState.animateScrollTo(target.coerceIn(0, scrollState.maxValue))
		}
	}
	Surface(
		modifier = Modifier
			.then(if (maxBarWidth != null) Modifier.widthIn(max = maxBarWidth) else Modifier)
			.shadow(8.dp, RoundedCornerShape(50))
			.wrapContentWidth(),
		shape = RoundedCornerShape(50),
		color = Color(colors.container),
		contentColor = MaterialTheme.colorScheme.onSurface,
	) {
		Row(
			modifier = Modifier
				.then(if (maxBarWidth != null) Modifier.horizontalScroll(scrollState) else Modifier)
				.heightIn(min = 64.dp)
				.padding(horizontal = 8.dp, vertical = 8.dp)
				.animateContentSize(animationSpec = SpringSize),
			horizontalArrangement = Arrangement.spacedBy(metrics.spacing),
			verticalAlignment = Alignment.CenterVertically,
		) {
			items.forEach { item ->
				FloatingNavItem(
					item = item,
					selected = item.id == selectedId,
					showLabel = showSelectedLabel,
					colors = colors,
					itemWidth = metrics.itemWidth,
					onClick = {
						if (item.id == selectedId) {
							onItemReselected(item.id)
						} else {
							onItemSelected(item.id)
						}
					},
				)
			}
		}
	}
}

@Composable
private fun FloatingContinueVisibility(
	visible: Boolean,
	colors: FloatingNavBarColors,
	onContinueClick: () -> Unit,
) {
	AnimatedVisibility(
		visible = visible,
		enter = fadeIn(animationSpec = SpringFloat) +
			expandHorizontally(animationSpec = SpringSize, expandFrom = Alignment.Start),
		exit = fadeOut(animationSpec = SpringFloat) +
			shrinkHorizontally(animationSpec = SpringSize, shrinkTowards = Alignment.Start),
	) {
		FloatingContinueButton(colors = colors, onClick = onContinueClick)
	}
}

@Composable
private fun FloatingContinueButton(
	colors: FloatingNavBarColors,
	onClick: () -> Unit,
) {
	val container by animateColorAsState(
		targetValue = Color(colors.selectedContainer),
		animationSpec = SpringColor,
		label = "continueContainer",
	)
	val content by animateColorAsState(
		targetValue = Color(colors.selectedContent),
		animationSpec = SpringColor,
		label = "continueContent",
	)
	val label = stringResource(R.string._continue)
	Surface(
		onClick = onClick,
		shape = CircleShape,
		color = container,
		contentColor = content,
		shadowElevation = 8.dp,
		modifier = Modifier
			.size(56.dp)
			.semantics { contentDescription = label },
	) {
		Box(contentAlignment = Alignment.Center) {
			Icon(
				painter = painterResource(R.drawable.ic_read),
				contentDescription = null,
				tint = content,
				modifier = Modifier.size(24.dp),
			)
		}
	}
}

@Composable
private fun FloatingNavItem(
	item: FloatingNavBarItem,
	selected: Boolean,
	showLabel: Boolean,
	colors: FloatingNavBarColors,
	itemWidth: Dp,
	onClick: () -> Unit,
) {
	val container by animateColorAsState(
		targetValue = if (selected) Color(colors.selectedContainer) else Color.Transparent,
		animationSpec = SpringColor,
		label = "navItemContainer",
	)
	val content by animateColorAsState(
		targetValue = if (selected) Color(colors.selectedContent) else Color(colors.unselectedContent),
		animationSpec = SpringColor,
		label = "navItemContent",
	)
	val title = stringResource(item.titleRes)
	val interactionSource = remember { MutableInteractionSource() }

	Box(
		modifier = Modifier
			.height(48.dp)
			.widthIn(min = itemWidth)
			.background(color = container, shape = CircleShape)
			.clickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick,
			)
			.semantics {
				this.selected = selected
				role = Role.Tab
				contentDescription = title
			},
		contentAlignment = Alignment.Center,
	) {
		Row(
			modifier = Modifier.padding(horizontal = (itemWidth - 24.dp) / 2),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.Center,
		) {
			BadgedBox(
				badge = {
					if (item.badgeCount > 0) {
						Badge { Text(text = if (item.badgeCount > 99) "99+" else item.badgeCount.toString()) }
					} else if (item.badgeCount < 0) {
						Badge()
					}
				},
			) {
				NavIcon(
					resId = item.icon,
					selected = selected,
					tint = content,
					modifier = Modifier.size(24.dp),
				)
			}
			AnimatedVisibility(
				visible = selected && showLabel,
				enter = expandHorizontally(animationSpec = SpringSize, expandFrom = Alignment.Start) +
					fadeIn(animationSpec = SpringFloat),
				exit = shrinkHorizontally(animationSpec = SpringSize, shrinkTowards = Alignment.Start) +
					fadeOut(animationSpec = SpringFloat),
			) {
				Text(
					text = title,
					color = content,
					style = MaterialTheme.typography.labelMedium,
					maxLines = 1,
					modifier = Modifier.padding(start = 8.dp),
				)
			}
		}
	}
}

private val StateChecked = intArrayOf(android.R.attr.state_checked)
private val StateUnchecked = intArrayOf(-android.R.attr.state_checked)

/**
 * Hosts a real [ImageView] so the nav icons' `<animated-selector>` transitions actually run.
 * Painting an AnimatedVectorDrawable through a Compose Painter does not reliably drive the platform
 * animator; an ImageView does, because that is the path the native BottomNavigationView uses.
 * [selected] drives the drawable state, which is what triggers the morph between the two items.
 */
@Composable
private fun NavIcon(
	@DrawableRes resId: Int,
	selected: Boolean,
	tint: Color,
	modifier: Modifier = Modifier,
) {
	AndroidView(
		modifier = modifier,
		factory = { ctx ->
			ImageView(ctx).apply {
				scaleType = ImageView.ScaleType.FIT_CENTER
				setImageResource(resId)
				// Prime the state without a transition, so the morph does not fire on first paint.
				setImageState(IntArray(0), false)
				jumpDrawablesToCurrentState()
			}
		},
		update = { imageView ->
			val targetResId = when {
				resId == R.drawable.ic_explore_selector && selected -> R.drawable.ic_explore_checked
				resId == R.drawable.ic_explore_selector -> R.drawable.ic_explore_normal
				else -> resId
			}
			val resourceChanged = imageView.tag != targetResId
			if (resourceChanged) {
				imageView.setImageResource(targetResId)
				imageView.tag = targetResId
			}
			if (resourceChanged || imageView.isSelected != selected) {
				imageView.isSelected = selected
				imageView.isActivated = selected
				imageView.setImageState(if (selected) StateChecked else StateUnchecked, false)
			}
			val tintColor = tint.toArgb()
			imageView.imageTintList = ColorStateList.valueOf(tintColor)
			imageView.drawable?.mutate()?.setTint(tintColor)
			imageView.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
		},
	)
}
