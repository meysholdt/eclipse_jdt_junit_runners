/*******************************************************************************
 * Copyright (c) 2000, 2023 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Brock Janiczak (brockj@tpg.com.au)
 *         - https://bugs.eclipse.org/bugs/show_bug.cgi?id=102236: [JUnit] display execution time next to each test
 *******************************************************************************/

package org.eclipse.jdt.internal.junit.ui;

import java.text.NumberFormat;

import org.eclipse.jdt.internal.junit.BasicElementLabels;
import org.eclipse.jdt.internal.junit.Messages;
import org.eclipse.jdt.internal.junit.model.TestCaseElement;
import org.eclipse.jdt.internal.junit.model.TestSuiteElement;
import org.eclipse.jdt.junit.model.ITestCaseElement;
import org.eclipse.jdt.junit.model.ITestElement;
import org.eclipse.jdt.junit.model.ITestRunSession;
import org.eclipse.jdt.junit.model.ITestSuiteElement;
import org.eclipse.jdt.junit.runners.IRunnerUIHandler;
import org.eclipse.jdt.junit.runners.RunnerUIHandlerRegistry;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;

@SuppressWarnings("restriction")
public class TestSessionLabelProviderCustom extends LabelProvider implements IStyledLabelProvider {

	private final TestRunnerViewPart fTestRunnerPart;
	private final int fLayoutMode;
	private final NumberFormat timeFormat;

	private boolean fShowTime;

	public TestSessionLabelProviderCustom(TestRunnerViewPart testRunnerPart, int layoutMode) {
		fTestRunnerPart = testRunnerPart;
		fLayoutMode = layoutMode;
		fShowTime = true;

		timeFormat = NumberFormat.getNumberInstance();
		timeFormat.setGroupingUsed(true);
		timeFormat.setMinimumFractionDigits(3);
		timeFormat.setMaximumFractionDigits(3);
		timeFormat.setMinimumIntegerDigits(1);
	}

	public StyledString getStyledText(Object element) {
		ITestElement testElement = (ITestElement) element;
		IRunnerUIHandler handler = RunnerUIHandlerRegistry.getHandler(testElement);
		StyledString text = null;
		if (handler != null)
			text = handler.getStyledLabel(fTestRunnerPart, testElement, fLayoutMode);
		if (text == null) {
			String label = getSimpleLabel(element);
			if (label == null) {
				return new StyledString(element.toString());
			}
			text = new StyledString(label);
		}
		String label = text.getString();

		if (fLayoutMode == TestRunnerViewPart.LAYOUT_HIERARCHICAL) {
			if (testElement.getParentContainer() instanceof ITestRunSession) {
				String testKindDisplayName = fTestRunnerPart.getTestKindDisplayName();
				if (testKindDisplayName != null) {
					String decorated = Messages.format(JUnitMessages.TestSessionLabelProvider_testName_JUnitVersion, new Object[] { label,
							testKindDisplayName });
					text = StyledCellLabelProvider.styleDecoratedString(decorated, StyledString.QUALIFIER_STYLER, text);
				}
			}

		} else {
			if (element instanceof ITestCaseElement) {
				String className = BasicElementLabels.getJavaElementName(((ITestCaseElement) element).getTestClassName());
				String decorated = Messages.format(JUnitMessages.TestSessionLabelProvider_testMethodName_className, new Object[] { label,
						className });
				text = StyledCellLabelProvider.styleDecoratedString(decorated, StyledString.QUALIFIER_STYLER, text);
			}
		}
		return addElapsedTime(text, testElement.getElapsedTimeInSeconds());
	}

	private StyledString addElapsedTime(StyledString styledString, double time) {
		String string = styledString.getString();
		String decorated = addElapsedTime(string, time);
		return StyledCellLabelProvider.styleDecoratedString(decorated, StyledString.COUNTER_STYLER, styledString);
	}

	private String addElapsedTime(String string, double time) {
		if (!fShowTime || Double.isNaN(time)) {
			return string;
		}
		String formattedTime = timeFormat.format(time);
		return Messages
				.format(JUnitMessages.TestSessionLabelProvider_testName_elapsedTimeInSeconds, new String[] { string, formattedTime });
	}

	private String getSimpleLabel(Object element) {
		if (element instanceof ITestElement) {
			IRunnerUIHandler handler = RunnerUIHandlerRegistry.getHandler((ITestElement) element);
			if (handler != null) {
				String result = handler.getSimpleLabel(fTestRunnerPart, (ITestElement) element);
				if (result != null)
					return result;
			}
		}
		if (element instanceof TestCaseElement) {
			TestCaseElement testCaseElement= (TestCaseElement) element;
			String displayName= testCaseElement.getDisplayName();
			return BasicElementLabels.getJavaElementName(displayName != null ? displayName : testCaseElement.getTestMethodName());
		} else if (element instanceof TestSuiteElement) {
			TestSuiteElement testSuiteElement= (TestSuiteElement) element;
			String displayName= testSuiteElement.getDisplayName();
			return BasicElementLabels.getJavaElementName(displayName != null ? displayName : testSuiteElement.getSuiteTypeName());
		}
		return null;
	}

	@Override
	public String getText(Object element) {
		String label = getSimpleLabel(element);
		if (label == null) {
			return element.toString();
		}
		ITestElement testElement = (ITestElement) element;
		if (fLayoutMode == TestRunnerViewPart.LAYOUT_HIERARCHICAL) {
			if (testElement.getParentContainer() instanceof ITestRunSession) {
				String testKindDisplayName = fTestRunnerPart.getTestKindDisplayName();
				if (testKindDisplayName != null) {
					label = Messages.format(JUnitMessages.TestSessionLabelProvider_testName_JUnitVersion, new Object[] { label,
							testKindDisplayName });
				}
			}
		} else {
			if (element instanceof ITestCaseElement) {
				String className = BasicElementLabels.getJavaElementName(((ITestCaseElement) element).getTestClassName());
				label = Messages.format(JUnitMessages.TestSessionLabelProvider_testMethodName_className, new Object[] { label, className });
			}
		}
		return addElapsedTime(label, testElement.getElapsedTimeInSeconds());
	}

	@Override
	public Image getImage(Object element) {
		return new TestSessionLabelProvider(fTestRunnerPart, fLayoutMode).getImage(element);
	}

	public void setShowTime(boolean showTime) {
		fShowTime = showTime;
		fireLabelProviderChanged(new LabelProviderChangedEvent(this));
	}

}
