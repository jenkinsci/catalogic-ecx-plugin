package com.catalogic.ecxjenkins.ecxjenkins;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import com.catalogic.ecx.sdk.ECXSdk;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

/**
 * Sample {@link Builder}.
 * <p>
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link ECXCDMBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 * <p>
 * <p>
 * When a build is performed, the {@link #perform} method will be invoked.
 *
 * @author Kohsuke Kawaguchi
 */
public class ECXCDMBuilder extends Builder implements SimpleBuildStep {

    private final String name;
    private final String password;
    private final String url;
    private final String job;
    private boolean production;
    private final int maxWaitTime;
    private final static int minWaitTime = 10;
    private final static int second = 1000;
    private final static int resolution = 2;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public ECXCDMBuilder(String name, String password, String url, String job, boolean production, int maxWaitTime) {

        this.name = name;
        this.password = password;
        this.url = url;
        this.production = production;
        this.maxWaitTime = maxWaitTime;
        this.job = job;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    public String getUrl() {
        return url;
    }

    public String getJob() {
        return job;
    }

    public int getMaxWaitTime() {
        return maxWaitTime;
    }
public boolean getProduction(){
	return this.production;
}
    @Override
    public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws AbortException {
        // This is where you 'build' the project.
        // Since this is a dummy, we just say 'hello world' and call that a build.

        // This also shows how you can consult the global configuration of the builder
      //  if (getDescriptor().getUseFrench())
    	
    	if (getDescriptor().getProduction())
            listener.getLogger().println("Bonjour, " + name + "!");
        else
            listener.getLogger().println("Executing an ECX CDM Job using the following information, " + name + " " + url);

    	ECXSdk ecx = new ECXSdk(name, password, url, production);
        ecx.connect();
        ecx.runJob(job);

        String msg = "";
        String lastMessage = "";

        int wait = 0;

        /**
         * TODO - job monitor is not expected to have FAILED, PARTIAL or COMPLETED. Fix this.
         */
        while (msg.compareTo("PARTIAL") != 0 && msg.compareTo("FAILED") != 0 && msg.compareTo("COMPLETED") != 0 && msg.compareTo("IDLE") != 0 && wait < (maxWaitTime * resolution)) {

            wait++;
            msg = getECXJobInfo(ecx, true);

            if (!StringUtils.isEmpty(msg) && msg.compareTo(lastMessage) != 0) {

                listener.getLogger().println(msg);
            }

            try {
                Thread.sleep(second / resolution);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            lastMessage = msg;
        }
        if (!StringUtils.isEmpty(msg)) {
            if (msg.compareTo("ACTIVE") == 0) {
                throw new AbortException("ECX CDM Build step for job " + job + " timed out waiting for the ECX job to complete! You may want to check ECX to see how long the job ran and potentially increase the configuration timeout.");
            }
        }
        msg = getECXJobInfo(ecx, false);
        listener.getLogger().println(msg);

        if (!StringUtils.isEmpty(msg)) {
            if (msg.compareTo("FAILED") == 0) {
                throw new AbortException("ECX " + job + " Failed!");
            }
        }
    }

    private String getECXJobInfo(ECXSdk ecx, boolean monitor) {

        if (monitor) {
            ecx.monitorJob(job);
        } else {
            ecx.getJobResult(job);
        }

        Iterator<String> _i = ecx.getStatus();
        String msg = (String) _i.next();
        _i.remove();

        return msg;

    }

    // Overridden for better type safety.
// If your plugin doesn't really define any property on Descriptor,
// you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link ECXCDMBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     * <p>
     * <p>
     * See <tt>src/main/resources/hudson/plugins/hello_world/ECXCDMBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         * <p>
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
     //   private boolean useFrench;
        private boolean production;

        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value This parameter receives the value that the user has typed.
         * @return Indicates the outcome of the validation. This is sent to the browser.
         * <p>
         * Note that returning {@link FormValidation#error(String)} does not
         * prevent the form from being saved. It just means that a message
         * will be displayed to the user.
         */
        public FormValidation doCheckName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please enter a valid ECX user name!");
            return FormValidation.ok();
        }

        public FormValidation doCheckPassword(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please enter a valid ECX password!");
            return FormValidation.ok();
        }

        public FormValidation doCheckMaxWaitTime(@QueryParameter String value) {
            try {
                Integer time = Integer.parseInt(value);
                if (time.intValue() < minWaitTime) {
                    return FormValidation.error("The max wait time in seconds should be a number greater than 10.");
                }
                return FormValidation.ok();
            } catch (NumberFormatException e) {
                return FormValidation.error("The max wait time needs to be a number.");
            }
        }

        public FormValidation doCheckJob(@QueryParameter String value) {
            try {
                Integer.parseInt(value);
                return FormValidation.ok();
            } catch (NumberFormatException e) {
                return FormValidation.error("Please check to ensure you are using a valid job id.");
            }
        }

        public FormValidation doTestConnection(@QueryParameter("name") final String name,
                                               @QueryParameter("password") final String password,
                                               @QueryParameter("url") final String url,
                                               @QueryParameter("production") final boolean production
        ) throws IOException, ServletException {
            try {

                ECXSdk ecx = new ECXSdk(name, password, url, production);
                ecx.connect();
                return FormValidation.ok("Success");
            } catch (Exception e) {
                return FormValidation.error("Client error : " + e.getMessage());
            }
        }

        public ListBoxModel doFillJobListItems(
                @QueryParameter("name") final String name,
                @QueryParameter("password") final String password,
                @QueryParameter("url") final String url,
                @QueryParameter("production") final boolean production
        ) {

            ListBoxModel _items = new ListBoxModel();
            ECXSdk ecx = new ECXSdk(name, password, url, production);
            ecx.connect();
            ecx.setJobList();

            Iterator it = ecx.getJobList().entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry) it.next();
                _items.add((String) pair.getValue(), (String) pair.getKey());
                it.remove(); // avoids a ConcurrentModificationException
            }
            return _items;

        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Catalogic ECX CDM Integration";
        }


        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
          //  useFrench = formData.getBoolean("useFrench");
        	production = formData.getBoolean("production");
            
            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
            save();
            return super.configure(req, formData);
        }

        /**
         * This method returns true if the global configuration says we should speak French.
         * <p>
         * The method name is bit awkward because global.jelly calls this method to determine
         * the initial state of the checkbox by the naming convention.
         */
     //   public boolean getUseFrench() {
      //      return useFrench;
     //   }
        public boolean getProduction() {
            return production;
        }
        
    }
}

