package io.cresco.proxyshield;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

    @Override
    public void start(BundleContext context) throws Exception {
        System.out.println("Proxy Shield Manager Bundle Started");
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        System.out.println("Proxy Shield Manager Bundle Stopped");
    }
}
