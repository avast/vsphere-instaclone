# ChangeLog - vsphere-instaclone

## vsphere-instaclone v2.0

This release contains many updates for easy manipulation with tens of instaprofiles.
It's possible to run this version together with the previous 1.x version. Older plugin version instances remains untouched.

### New
- Centralized support for managing multiple accounts 
  * It's necessary to use REST API with an encrypted content to define accounts
-  REST API to work with profiles (CRUD) 
-  UI:
    * Profiles settings UI contains combobox with selection
    *  "flat" view for profiles used defined by this plugin - `http://<teamcityurl>/vmic.html`
- quite [new app](https://github.com/avast/instaprofiles-sync) for syncing profiles defined by "code" in Git and TC 

### Changed 

- enhanced logging
- plugin's cloud code changed from value VMIC ➔ VMIC2








