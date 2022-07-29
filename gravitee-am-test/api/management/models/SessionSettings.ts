/* tslint:disable */
/* eslint-disable */
/**
 * Gravitee.io - Access Management API
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */

import { exists, mapValues } from '../runtime';
/**
 * 
 * @export
 * @interface SessionSettings
 */
export interface SessionSettings {
    /**
     * 
     * @type {boolean}
     * @memberof SessionSettings
     */
    persistent?: boolean;
}

export function SessionSettingsFromJSON(json: any): SessionSettings {
    return SessionSettingsFromJSONTyped(json, false);
}

export function SessionSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): SessionSettings {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'persistent': !exists(json, 'persistent') ? undefined : json['persistent'],
    };
}

export function SessionSettingsToJSON(value?: SessionSettings | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
        'persistent': value.persistent,
    };
}
