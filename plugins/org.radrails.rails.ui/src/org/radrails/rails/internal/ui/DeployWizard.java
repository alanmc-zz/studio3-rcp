package org.radrails.rails.internal.ui;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWizard;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.radrails.rails.ui.RailsUIPlugin;

import com.aptana.git.core.GitPlugin;
import com.aptana.git.core.model.GitRepository;
import com.aptana.git.core.model.IGitRepositoryManager;

public class DeployWizard extends Wizard implements IWorkbenchWizard
{

	private IProject project;

	@Override
	public boolean performFinish()
	{
		IRunnableWithProgress runnable = null;
		// check what the user chose, then do the heavy lifting, or tell the page to finish...
		IWizardPage currentPage = getContainer().getCurrentPage();
		if (currentPage.getName().equals(HerokuDeployWizardPage.NAME))
		{
			HerokuDeployWizardPage page = (HerokuDeployWizardPage) currentPage;
			runnable = createHerokuDeployRunnable(page);
		}
		else if (currentPage.getName().equals(FTPDeployWizardPage.NAME))
		{
			// TODO Set up FTP deployment stuff, run it
		}
		else if (currentPage.getName().equals(HerokuSignupPage.NAME))
		{
			HerokuSignupPage page = (HerokuSignupPage) currentPage;
			runnable = createHerokuSignupRunnable(page);
		}
		// TODO Add branch for capistrano deployment

		if (runnable != null)
		{
			try
			{
				getContainer().run(true, false, runnable);
			}
			catch (Exception e)
			{
				RailsUIPlugin.logError(e);
			}
		}
		return true;
	}

	protected IRunnableWithProgress createHerokuDeployRunnable(HerokuDeployWizardPage page)
	{
		IRunnableWithProgress runnable;
		final String appName = page.getAppName();
		final boolean publishImmediately = page.publishImmediately();
		runnable = new IRunnableWithProgress()
		{

			@Override
			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException
			{
				SubMonitor sub = SubMonitor.convert(monitor, 100);
				try
				{
					// Initialize git repo for project if necessary
					// TODO use new createOrAttach method once code from github branch is merged over!
					IGitRepositoryManager manager = GitPlugin.getDefault().getGitRepositoryManager();
					GitRepository repo = manager.getUnattachedExisting(project.getLocationURI());
					boolean created = false;
					if (repo == null)
					{
						manager.create(new File(project.getLocationURI()).getAbsolutePath());
						created = true;
					}
					sub.worked(10);
					repo = manager.attachExisting(project, sub.newChild(10));

					// Now do an initial commit
					if (created)
					{
						if (!repo.isDirty())
						{
							repo.index().refresh(sub.newChild(15));
						}
						repo.index().stageFiles(repo.index().changedFiles());
						repo.index().commit("Initial Commit");
					}
					// TODO What if we didn't create the repo right now, but it is "dirty"?
					sub.setWorkRemaining(60);

					// TODO Now publish the project if publish immediately is true!
				}
				catch (CoreException ce)
				{
					throw new InvocationTargetException(ce);
				}
				finally
				{
					sub.done();
				}
			}
		};
		return runnable;
	}

	protected IRunnableWithProgress createHerokuSignupRunnable(HerokuSignupPage page)
	{
		IRunnableWithProgress runnable;
		final String userID = page.getUserID();
		runnable = new IRunnableWithProgress()
		{

			@Override
			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException
			{
				SubMonitor sub = SubMonitor.convert(monitor, 100);
				try
				{
					// TODO Send a ping to aptana.com with email address for referral tracking (uh, what URL am I
					// hitting and with what data?)
					sub.worked(25);
					// Bring up Heroku signup page, http://api.heroku.com/signup
					IWorkbenchBrowserSupport support = PlatformUI.getWorkbench().getBrowserSupport();
					IWebBrowser browser = support.createBrowser("heroku-signup"); //$NON-NLS-1$
					browser.openURL(new URL("http://api.heroku.com/signup")); //$NON-NLS-1$
					sub.worked(50);
					// TODO Inject special JS into it. Need to fill in id of 'invitation_email' with the value!
					// This seems bizzare, can't we somehow send a query param to populate the email address?, or better
					// yet, just post to the page as if the user filled out the form?
				}
				catch (Exception e)
				{
					throw new InvocationTargetException(e);
				}
				finally
				{
					sub.done();
				}
			}
		};
		return runnable;
	}

	@Override
	public void addPages()
	{
		// Add the first basic page where they choose the deployment option
		addPage(new DeployWizardPage());
		setForcePreviousAndNextButtons(true); // we only add one page here, but we calculate the next page
												// dynamically...
	}

	@Override
	public IWizardPage getNextPage(IWizardPage page)
	{
		// delegate to page because we modify the page list dynamically and don't statically add them via addPage
		return page.getNextPage();
	}

	@Override
	public IWizardPage getPreviousPage(IWizardPage page)
	{
		// delegate to page because we modify the page list dynamically and don't statically add them via addPage
		return page.getPreviousPage();
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection)
	{
		Object element = selection.getFirstElement();
		if (element instanceof IResource)
		{
			IResource resource = (IResource) element;
			this.project = resource.getProject();
		}
	}

	public IProject getProject()
	{
		return project;
	}

	@Override
	public boolean canFinish()
	{
		IWizardPage page = getContainer().getCurrentPage();
		return page.isPageComplete() && page.getNextPage() == null;
	}

}
