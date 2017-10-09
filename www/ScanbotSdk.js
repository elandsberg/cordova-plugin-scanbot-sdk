
function _cordova_exec(actionName, successCallback, errorCallback, options) {
  cordova.exec(successCallback, errorCallback, "ScanbotSdk", actionName, (options ? [options] : []));
}

module.exports = {

  // ------------------------------------------------
  // Scanbot SDK functions:

  initializeSdk: function(successCallback, errorCallback, options) {
    _cordova_exec("initializeSdk", successCallback, errorCallback, options);
  },

  documentDetection: function(successCallback, errorCallback, options) {
    _cordova_exec("documentDetection", successCallback, errorCallback, options);
  },

  applyImageFilter: function(successCallback, errorCallback, options) {
    _cordova_exec("applyImageFilter", successCallback, errorCallback, options);
  },

  createPdf: function(successCallback, errorCallback, options) {
    _cordova_exec("createPdf", successCallback, errorCallback, options);
  },

  performOcr: function(successCallback, errorCallback, options) {
    _cordova_exec("performOcr", successCallback, errorCallback, options);
  },

  getOcrConfigs: function(successCallback, errorCallback, options) {
    _cordova_exec("getOcrConfigs", successCallback, errorCallback, options);
  },

  cleanup: function(successCallback, errorCallback, options) {
    _cordova_exec("cleanup", successCallback, errorCallback, options);
  },

  // ------------------------------------------------


  // ------------------------------------------------
  // Scanbot SDK constants:

  ImageFilter: {
    NONE: "NONE",
    COLOR_ENHANCED: "COLOR_ENHANCED",
    GRAYSCALE: "GRAYSCALE",
    BINARIZED: "BINARIZED"
  },

  DetectionResult: {
    OK: "OK",
    OK_BUT_BAD_ANGLES: "OK_BUT_BAD_ANGLES",
    OK_BUT_BAD_ASPECT_RATIO: "OK_BUT_BAD_ASPECT_RATIO",
    OK_BUT_TOO_SMALL: "OK_BUT_TOO_SMALL",
    ERROR_TOO_DARK: "ERROR_TOO_DARK",
    ERROR_TOO_NOISY: "ERROR_TOO_NOISY",
    ERROR_NOTHING_DETECTED: "ERROR_NOTHING_DETECTED"
  },
  
  OcrOutputFormat: {
    PDF_FILE: "PDF_FILE",
    PLAIN_TEXT: "PLAIN_TEXT",
    FULL_OCR_RESULT: "FULL_OCR_RESULT"
  }
  // ------------------------------------------------

};
