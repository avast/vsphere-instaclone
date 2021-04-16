function enableOrDisableProfile(projectId, profileId, action) {
    BS.ajaxRequest(window['base_uri'] + "/clouds/admin/cloudAdminProfile.html", {
        parameters : {
            action : action,
            profileId : profileId,
            projectId: projectId
        },
        method : 'post',
        onComplete: function(transport) {
    //        var handled = that.processKillError(transport);
          //  if (!handled) {
                document.location.reload(true)
//            }
  //          that.postSubmit();
        }
    });
}