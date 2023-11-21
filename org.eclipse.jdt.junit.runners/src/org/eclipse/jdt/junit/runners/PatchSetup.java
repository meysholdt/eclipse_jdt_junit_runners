package org.eclipse.jdt.junit.runners;

import static org.eclipse.debug.core.ILaunchManager.DEBUG_MODE;
import static org.eclipse.debug.core.ILaunchManager.RUN_MODE;
import static org.eclipse.jdt.internal.junit.ui.JUnitMessages.RerunAction_label_debug;
import static org.eclipse.jdt.internal.junit.ui.JUnitMessages.RerunAction_label_run;
import static org.eclipse.jdt.junit.runners.ReflectionUtil.getParentObject;
import static org.eclipse.jdt.junit.runners.ReflectionUtil.readField;

import java.util.ArrayList;

import org.eclipse.core.runtime.ListenerList;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.junit.model.TestCaseElement;
import org.eclipse.jdt.internal.junit.model.TestElement;
import org.eclipse.jdt.internal.junit.model.TestSuiteElement;
import org.eclipse.jdt.internal.junit.ui.OpenTestActionCustom;
import org.eclipse.jdt.internal.junit.ui.RerunAction;
import org.eclipse.jdt.internal.junit.ui.TestRunnerViewPart;
import org.eclipse.jdt.internal.junit.ui.TestSessionLabelProviderCustom;
import org.eclipse.jdt.internal.junit.ui.TestViewer;
import org.eclipse.jdt.internal.ui.viewsupport.ColoringLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.SelectionProviderMediator;
import org.eclipse.jdt.junit.model.ITestSuiteElement;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.TypedListener;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.IPageListener;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.PartSite;
import org.eclipse.ui.internal.PopupMenuExtender;

@SuppressWarnings("restriction")
public class PatchSetup implements IStartup {

	private final class MyTestOpenListener extends SelectionAdapter {
		private SelectionProviderMediator fSelectionProvider;

		private TestRunnerViewPart fTestRunnerPart;

		public MyTestOpenListener(TestRunnerViewPart fTestRunnerPart, SelectionProviderMediator fSelectionProvider) {
			super();
			this.fTestRunnerPart = fTestRunnerPart;
			this.fSelectionProvider = fSelectionProvider;
		}

		@Override
		public void widgetDefaultSelected(SelectionEvent e) {
			IStructuredSelection selection = (IStructuredSelection) fSelectionProvider.getSelection();
			if (selection.size() != 1)
				return;

			TestElement testElement = (TestElement) selection.getFirstElement();

			IRunnerUIHandler uiHandler = RunnerUIHandlerRegistry.getHandler(testElement);
			if (uiHandler != null && uiHandler.handleDoubleClick(fTestRunnerPart, testElement))
				return;

			OpenTestActionCustom action;
			if (testElement instanceof TestSuiteElement) {
				action = new OpenTestActionCustom(fTestRunnerPart, testElement.getTestName());
			} else if (testElement instanceof TestCaseElement) {
				TestCaseElement testCase = (TestCaseElement) testElement;
				action = new OpenTestActionCustom(fTestRunnerPart, testCase);
			} else {
				throw new IllegalStateException(String.valueOf(testElement));
			}

			if (action.isEnabled())
				action.run();
		}
	}

	private class WorkbenchListener implements IWindowListener, IPageListener, IPartListener {

		public void pageActivated(IWorkbenchPage page) {
		}

		public void pageClosed(IWorkbenchPage page) {
			page.removePartListener(listener);
		}

		public void pageOpened(IWorkbenchPage page) {
			page.addPartListener(listener);
		}

		public void partActivated(IWorkbenchPart part) {
		}

		public void partBroughtToTop(IWorkbenchPart part) {
		}

		public void partClosed(IWorkbenchPart part) {
		}

		public void partDeactivated(IWorkbenchPart part) {

		}

		public void partOpened(IWorkbenchPart part) {
			checkPart(part);
		}

		public void windowActivated(IWorkbenchWindow window) {
		}

		public void windowClosed(IWorkbenchWindow window) {
			window.removePageListener(listener);
		}

		public void windowDeactivated(IWorkbenchWindow window) {
		}

		public void windowOpened(IWorkbenchWindow window) {
			window.addPageListener(listener);
		}

	}

	private WorkbenchListener listener = new WorkbenchListener();

	private void checkPart(IWorkbenchPart part) {
		if (part instanceof TestRunnerViewPart) {
			TestRunnerViewPart testpart = (TestRunnerViewPart) part;
			TestViewer viewer = readField(part, "fTestViewer", TestViewer.class);
			TreeViewer treeViever = readField(viewer, "fTreeViewer", TreeViewer.class);
			TableViewer tableViever = readField(viewer, "fTableViewer", TableViewer.class);

			TypedListener treeListener = findSelctionListener(treeViever.getTree(), TestViewer.class);
			TypedListener tableListener = findSelctionListener(tableViever.getTable(), TestViewer.class);
			if (treeListener == null || tableListener == null)
				return;
			SelectionProviderMediator selectionProvider = readField(viewer, "fSelectionProvider", SelectionProviderMediator.class);
			MyTestOpenListener newListener = new MyTestOpenListener(testpart, selectionProvider);
			replaceListener(treeViever.getTree(), treeListener, newListener);
			replaceListener(tableViever.getTable(), tableListener, newListener);

			((ColoringLabelProvider) treeViever.getLabelProvider()).getStyledStringProvider().dispose();
			treeViever.getLabelProvider().dispose();
			treeViever.setLabelProvider(new ColoringLabelProvider(new TestSessionLabelProviderCustom(testpart, 1)));

			ArrayList<?> menus = readField(PartSite.class, part.getSite(), "menuExtenders", ArrayList.class);
			for (Object m : menus)
				if (m instanceof PopupMenuExtender) {
					ListenerList listeners = readField(MenuManager.class, ((PopupMenuExtender) m).getManager(), "listeners",
							ListenerList.class);
					IMenuListener oldListener = null;
					for (Object l : listeners.getListeners())
						if (getParentObject(l) instanceof TestViewer)
							oldListener = (IMenuListener) l;
					if (oldListener != null) {
						listeners.remove(oldListener);
						listeners.add(new MenuExtension(testpart, selectionProvider));
						listeners.add(oldListener);
					}
				}
			// System.out.println(menus);

		}
	}

	private static class MenuExtension implements IMenuListener {

		private TestRunnerViewPart fTestRunnerPart;

		private SelectionProviderMediator fSelectionProvider;

		public MenuExtension(TestRunnerViewPart fTestRunnerPart, SelectionProviderMediator fSelectionProvider) {
			super();
			this.fSelectionProvider = fSelectionProvider;
			this.fTestRunnerPart = fTestRunnerPart;
		}

		public void menuAboutToShow(IMenuManager manager) {
			IStructuredSelection selection = (IStructuredSelection) fSelectionProvider.getSelection();
			if (selection.size() != 1)
				return;

			TestElement testElement = (TestElement) selection.getFirstElement();

			IRunnerUIHandler uiHandler = RunnerUIHandlerRegistry.getHandler(testElement);
			if (uiHandler != null)
				uiHandler.contextMenuAboutToShow(fTestRunnerPart, testElement, manager);

			// we add the run/debug actions for cases where
			// org.eclipse.jdt.internal.junit.ui.TestViewer.handleMenuAboutToShow(IMenuManager)
			// doesn't do it.
			if (!Activator.CAN_RUN_SUBTREES && testElement instanceof TestSuiteElement && !testClassExists(testElement.getClassName())) {
				IType type = TypeUtil.findType(testElement);
				if (type != null) {
					String className = type.getFullyQualifiedName();
					String id = testElement.getId();
					String testName = ((ITestSuiteElement) testElement).getSuiteTypeName();
					String displayName = testElement.getDisplayName();
					String uniqueId = testElement.getUniqueId();
					manager.add(new Separator());
					manager.add(new RerunAction(RerunAction_label_run, fTestRunnerPart, id, className, testName, displayName, uniqueId, RUN_MODE));
					manager.add(new RerunAction(RerunAction_label_debug, fTestRunnerPart, id, className, testName, displayName, uniqueId, DEBUG_MODE));
					manager.add(new Separator());
				}
			}
		}

		private boolean testClassExists(String className) {
			IJavaProject project = fTestRunnerPart.getLaunchedProject();
			if (project == null)
				return false;
			try {
				IType type = project.findType(className);
				return type != null;
			} catch (JavaModelException e) {
				// fall through
			}
			return false;
		}

	}

	public void earlyStartup() {
		final IWorkbench workbench = PlatformUI.getWorkbench();
		workbench.getDisplay().asyncExec(new Runnable() {
			public void run() {
				workbench.addWindowListener(listener);
				for (IWorkbenchWindow window : workbench.getWorkbenchWindows())
					for (IWorkbenchPage page : window.getPages()) {
						page.addPartListener(listener);
						for (IViewReference part : page.getViewReferences()) {
							checkPart(part.getView(false));
						}
					}
				// workbench.getActiveWorkbenchWindow().getSelectionService().addSelectionListener(new
				// MyListener());
			}
		});
	}

	private TypedListener findSelctionListener(Widget provider, Class<?> declaringClass) {
		for (Listener o : provider.getListeners(SWT.Selection))
			if (o instanceof TypedListener && ((TypedListener) o).getEventListener().getClass().getDeclaringClass() == declaringClass)
				return (TypedListener) o;
		return null;
	}

	private void replaceListener(Widget provider, TypedListener oldListener, SelectionListener newListener) {
		provider.removeListener(SWT.Selection, oldListener);
		provider.removeListener(SWT.DefaultSelection, oldListener);
		provider.addListener(SWT.Selection, new TypedListener(newListener));
		provider.addListener(SWT.DefaultSelection, new TypedListener(newListener));
	}

}
