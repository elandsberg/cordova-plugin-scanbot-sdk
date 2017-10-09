
function _cordova_exec(actionName, successCallback, errorCallback, options) {
  cordova.exec(successCallback, errorCallback, "ScanbotSdkUi", actionName, (options ? [options] : []));
}

module.exports = {

  // Scanbot SDK UI functions:

  startCamera: function(successCallback, errorCallback, options) {
    _cordova_exec("startCamera", successCallback, errorCallback, options);
  },

  dismissCamera: function() {
    _cordova_exec("dismissCamera");
  },

  startCropping: function(successCallback, errorCallback, options) {
    _cordova_exec("startCropping", successCallback, errorCallback, options);
  },

};
