// Em Windows a janela de console não deve aparecer em build de release.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    verbas_desktop_lib::run()
}
