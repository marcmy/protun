// Copyright (c) 2026 Proton AG
//
// This file is part of ProtonVPN.
//
// ProtonVPN is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// ProtonVPN is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.

use std::net::{Ipv4Addr, Ipv6Addr};

pub trait OptionIpv6AddrAsString {
    fn to_string_or(&self, none_text: &str) -> String;
}

impl OptionIpv6AddrAsString for Option<Ipv6Addr> {
    /// Creates a string representation of the IPv6 address if it exists, otherwise returns the 'none_text' provided
    fn to_string_or(&self, none_text: &str) -> String {
        match self {
            Some(addr) => addr.to_string(),
            None => none_text.to_string(),
        }
    }
}

pub trait OptionIpv4AddrAsString {
    fn to_string_or(&self, none_text: &str) -> String;
}

impl OptionIpv4AddrAsString for Option<Ipv4Addr> {
    /// Creates a string representation of the IPv4 address if it exists, otherwise returns the 'none_text' provided
    fn to_string_or(&self, none_text: &str) -> String {
        match self {
            Some(addr) => addr.to_string(),
            None => none_text.to_string(),
        }
    }
}