package com.avast.teamcity.plugins.instaclone.utils;

import com.vmware.vim25.*;
import org.apache.log4j.Logger;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;


/**
 * Utility class for the PropertyCollector API.
 *
 * @author Steve JIN (sjin@vmware.com)
 */
public class PropertyCollectorUtil {

    private static Logger log = Logger.getLogger(PropertyCollectorUtil.class);

    final public static Object NULL = new Object();

    /**
     * Method to convert an object to its type
     * For example when ArrayOfManagedObject is passed in
     * return a ManagedObject[]
     *
     * @param dynaPropVal
     * @return
     */
    public static Object convertProperty(Object dynaPropVal) {
        Object propertyValue = null;
        if (dynaPropVal == null) {
            throw new IllegalArgumentException("Unable to convertProperty on null object.");
        }
        Class<?> propClass = dynaPropVal.getClass();
        String propName = propClass.getName();
        //Check the dynamic propery for ArrayOfXXX object
        if (propName.contains("ArrayOf")) {
            String methodName = propName.substring(propName.indexOf("ArrayOf") + "ArrayOf".length());
            // If object is ArrayOfXXX object, then get the XXX[] by invoking getXXX() on the object. For Ex:
            // ArrayOfManagedObjectReference.getManagedObjectReference() returns ManagedObjectReference[] array.
            try {
                Method getMethod;
                try {
                    getMethod = propClass.getMethod("get" + methodName, (Class[]) null);
                }
                catch (NoSuchMethodException ignore) {
                    getMethod = propClass.getMethod("get_" + methodName.toLowerCase(), (Class[]) null);
                }
                propertyValue = getMethod.invoke(dynaPropVal, (Object[]) null);
            }
            catch (Exception e) {
                log.error("Exception caught trying to convertProperty", e);
            }
        }
        //Handle the case of an unwrapped array being deserialized.
        else if (dynaPropVal.getClass().isArray()) {
            propertyValue = dynaPropVal;
        }
        else {
            propertyValue = dynaPropVal;
        }

        return propertyValue;
    }

    public static ObjectSpec creatObjectSpec(ManagedObjectReference mor, boolean skip, SelectionSpec[] selSet) {
        ObjectSpec oSpec = new ObjectSpec();
        oSpec.setObj(mor);
        oSpec.setSkip(skip);
        oSpec.getSelectSet().addAll(Arrays.asList(selSet));
        return oSpec;
    }

    public static PropertySpec createPropertySpec(String type, boolean allProp, String[] pathSet) {
        PropertySpec pSpec = new PropertySpec();
        pSpec.setType(type);
        pSpec.setAll(allProp); //whether or not all properties of the object are read. If this property is set to true, the pathSet property is ignored.
        pSpec.getPathSet().addAll(Arrays.asList(pathSet));
        return pSpec;
    }

    public static SelectionSpec[] createSelectionSpec(String[] names) {
        SelectionSpec[] sss = new SelectionSpec[names.length];
        for (int i = 0; i < names.length; i++) {
            sss[i] = new SelectionSpec();
            sss[i].setName(names[i]);
        }
        return sss;
    }

    public static TraversalSpec createTraversalSpec(String name, String type, String path, String[] selectPath) {
        return createTraversalSpec(name, type, path, createSelectionSpec(selectPath));
    }

    public static TraversalSpec createTraversalSpec(String name, String type, String path, SelectionSpec[] selectSet) {
        TraversalSpec ts = new TraversalSpec();
        ts.setName(name);
        ts.setType(type);
        ts.setPath(path);
        ts.setSkip(Boolean.FALSE);
        ts.getSelectSet().addAll(Arrays.asList(selectSet));
        return ts;
    }

    /**
     * This code takes an array of [typename, property, property, ...]
     * and converts it into a PropertySpec[].
     *
     * @param typeProplists 2D array of type and properties to retrieve
     * @return Array of container filter specs
     */
    public static PropertySpec[] buildPropertySpecArray(String[][] typeProplists) {
        PropertySpec[] pSpecs = new PropertySpec[typeProplists.length];

        for (int i = 0; i < typeProplists.length; i++) {
            String type = typeProplists[i][0];
            String[] props = new String[typeProplists[i].length - 1];
            System.arraycopy(typeProplists[i], 1, props, 0, props.length);

            boolean all = (props.length == 0);
            pSpecs[i] = createPropertySpec(type, all, props);
        }
        return pSpecs;
    }

    /**
     * This method creates a SelectionSpec[] to traverses the entire
     * inventory tree starting at a Folder
     * NOTE: This full traversal is based on VC2/ESX3 inventory structure.
     * It does not search new ManagedEntities like Network, DVS, etc.
     * If you want a full traversal with VC4/ESX4, use buildFullTraversalV4().
     *
     * @return The SelectionSpec[]
     */
    public static SelectionSpec[] buildFullTraversal() {
        List<TraversalSpec> tSpecs = buildFullTraversalV2NoFolder();

        // Recurse through the folders
        TraversalSpec visitFolders = createTraversalSpec("visitFolders",
            "Folder", "childEntity",
            new String[]{"visitFolders", "dcToHf", "dcToVmf", "crToH", "crToRp", "HToVm", "rpToVm"});

        SelectionSpec[] sSpecs = new SelectionSpec[tSpecs.size() + 1];
        sSpecs[0] = visitFolders;
        for (int i = 1; i < sSpecs.length; i++) {
            sSpecs[i] = tSpecs.get(i - 1);
        }

        return sSpecs;
    }

    /**
     * This method creates basic set of TraveralSpec without visitFolders spec
     *
     * @return The TraversalSpec[]
     */
    private static List<TraversalSpec> buildFullTraversalV2NoFolder() {
        // Recurse through all ResourcePools
        TraversalSpec rpToRp = createTraversalSpec("rpToRp",
            "ResourcePool", "resourcePool",
            new String[]{"rpToRp", "rpToVm"});

        // Recurse through all ResourcePools
        TraversalSpec rpToVm = createTraversalSpec("rpToVm",
            "ResourcePool", "vm",
            new SelectionSpec[]{});

        // Traversal through ResourcePool branch
        TraversalSpec crToRp = createTraversalSpec("crToRp",
            "ComputeResource", "resourcePool",
            new String[]{"rpToRp", "rpToVm"});

        // Traversal through host branch
        TraversalSpec crToH = createTraversalSpec("crToH",
            "ComputeResource", "host",
            new SelectionSpec[]{});

        // Traversal through hostFolder branch
        TraversalSpec dcToHf = createTraversalSpec("dcToHf",
            "Datacenter", "hostFolder",
            new String[]{"visitFolders"});

        // Traversal through vmFolder branch
        TraversalSpec dcToVmf = createTraversalSpec("dcToVmf",
            "Datacenter", "vmFolder",
            new String[]{"visitFolders"});

        TraversalSpec HToVm = createTraversalSpec("HToVm",
            "HostSystem", "vm",
            new String[]{"visitFolders"});

        return Arrays.asList(dcToVmf, dcToHf, crToH, crToRp, rpToRp, HToVm, rpToVm);
    }

    /**
     * This method creates a SelectionSpec[] to traverses the entire
     * inventory tree starting at a Folder
     *
     * @return The SelectionSpec[]
     */
    public static SelectionSpec[] buildFullTraversalV4() {
        List<TraversalSpec> tSpecs = buildFullTraversalV2NoFolder();

        TraversalSpec dcToDs = createTraversalSpec("dcToDs",
            "Datacenter", "datastoreFolder",
            new String[]{"visitFolders"});

        TraversalSpec vAppToRp = createTraversalSpec("vAppToRp",
            "VirtualApp", "resourcePool",
            new String[]{"rpToRp", "vAppToRp"});

        /**
         * Copyright 2009 Altor Networks, contribution by Elsa Bignoli
         * @author Elsa Bignoli (elsa@altornetworks.com)
         */
        // Traversal through netFolder branch
        TraversalSpec dcToNetf = createTraversalSpec("dcToNetf",
            "Datacenter", "networkFolder",
            new String[]{"visitFolders"});

        // Recurse through the folders
        TraversalSpec visitFolders = createTraversalSpec("visitFolders",
            "Folder", "childEntity",
            new String[]{"visitFolders", "dcToHf", "dcToVmf", "dcToDs", "dcToNetf", "crToH", "crToRp", "HToVm", "rpToVm"});

        SelectionSpec[] sSpecs = new SelectionSpec[tSpecs.size() + 4];
        sSpecs[0] = visitFolders;
        sSpecs[1] = dcToDs;
        sSpecs[2] = dcToNetf;
        sSpecs[3] = vAppToRp;
        for (int i = 4; i < sSpecs.length; i++) {
            sSpecs[i] = tSpecs.get(i - 4);
        }

        return sSpecs;
    }

}