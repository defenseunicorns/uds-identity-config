/**
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

/**
 * Form data used for User Registration or login
 */
export interface RegistrationFormData {
  firstName?: string;
  lastName?: string;
  organization?: string;
  username?: string;
  email?: string;
  password?: string;
  affiliation?: string;
  payGrade?: string;
  cac_c?: string;
  cac_o?: string;
  cac_cn?: string;
}
