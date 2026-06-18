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

use std::time::{Duration, Instant};
use std::sync::mpsc::{Receiver, RecvTimeoutError};

#[derive(Default, Debug)]
pub struct DebouncingBatch {
    pub event_count: u32,
    pub first_input_at: Option<Instant>,
}

impl DebouncingBatch {
    fn absorb(&mut self) {
        self.event_count += 1;
        if self.first_input_at.is_none() {
            self.first_input_at = Some(Instant::now());
        }
    }
}

pub struct Debouncer {
}

impl Debouncer {
    pub fn start<TReceiver, TTrigger>(event_receiver: Receiver<TReceiver>, delay: Duration, mut on_trigger: TTrigger)
    where
        TTrigger: FnMut(DebouncingBatch)
    {
        let mut batch = DebouncingBatch::default();
        let mut deadline: Option<Instant> = None;

        loop {
            let wait = match deadline {
                Some(d) => d.saturating_duration_since(Instant::now()),
                None => Duration::from_secs(60 * 60),
            };

            match event_receiver.recv_timeout(wait) {
                Ok(_) => {
                    if batch.event_count == 0 {
                        log::debug!("Debouncer first input. There will be a {delay:?} window from the last input.");
                    }
                    batch.absorb();
                    deadline = Some(Instant::now() + delay);
                }

                Err(RecvTimeoutError::Timeout) => {
                    if batch.event_count > 0 {
                        let b = std::mem::take(&mut batch);
                        deadline = None;
                        log_trigger(&b);
                        on_trigger(b);
                    } else {
                        log::warn!("Debouncer had an unexpected timeout with zero events. The trigger will not be called.");
                    }
                }

                Err(RecvTimeoutError::Disconnected) => {
                    log::info!("Debouncer stopping due to event receiver disconnection.");
                    break;
                },
            }
        }
    }
}

fn log_trigger(b: &DebouncingBatch) {
    let elapsed: Duration = b.first_input_at.map(|t| t.elapsed()).unwrap_or_default();
    log::debug!("Debouncer is going to trigger now after {:?}: {} event(s)", elapsed, b.event_count);
}