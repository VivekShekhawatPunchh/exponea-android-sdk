package com.exponea.sdk.models

data class CustomerExportModel(var attributes: CustomerExportAttributes,
                               var filter: Array<KeyValueModel>,
                               var executionTime: Int,
                               var timezone: String,
                               var responseFormat: String)